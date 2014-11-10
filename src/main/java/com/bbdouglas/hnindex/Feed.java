package com.bbdouglas.hnindex;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.common.SolrInputDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

public class Feed {

    private static final String HN_UPDATE_URL = "https://hacker-news.firebaseio.com/v0/updates.json";
    private static final String SOLR_INSTANCE = "http://localhost:8983/solr/hnindex";
    private static final String HN_USER_URL_TEMPLATE = "https://hacker-news.firebaseio.com/v0/user/${userName}.json";

    private static Logger log = LoggerFactory.getLogger(Feed.class);
    private static ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) {

        CloseableHttpClient httpclient = HttpClients.createDefault();
        HttpGet httpGet = new HttpGet(HN_UPDATE_URL);
        httpGet.setHeader("Accept","text/event-stream");
        // The underlying HTTP connection is still held by the response object
        // to allow the response content to be streamed directly from the network socket.
        // In order to ensure correct deallocation of system resources
        // the user MUST call CloseableHttpResponse#close() from a finally clause.
        // Please note that if response content is not fully consumed the underlying
        // connection cannot be safely re-used and will be shut down and discarded
        // by the connection manager.
        try (CloseableHttpResponse response = httpclient.execute(httpGet)) {
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode >= 200 && statusCode < 300) {
                HttpEntity entity = response.getEntity();
                parseEventStream(entity.getContent());
                EntityUtils.consume(entity);
            } else {
                log.error("Problem fetching updates: {}", response.getStatusLine());
            }
        } catch (IOException e) {
            log.error("Problem with stream", e);
        }
    }

    /**
     * Reads a line of bytes from a stream, writing it to the output stream.
     * Does not write the newline characters.
     * @param in The input stream to read from.
     * @param out The output stream to write to.
     * @return True if EOF was reached in reading the line.
     */
    private static boolean readLine(InputStream in, OutputStream out)
            throws IOException {
        // TODO: This only covers newlines, not CR or CR/LF
        int c = in.read();
        while (c != -1) {
            if (c == (byte) '\n') {
                return false;
            }
            out.write(c);
            c = in.read();
        }
        return true;
    }

    private static void parseEventStream(InputStream stream) throws IOException {
        // A very basic and incomplete implementation of Server-Sent Events
        // See http://www.w3.org/TR/eventsource/
        boolean eof = false;
        boolean firstLine = true;
        EventBuilder eventBuilder = new EventBuilder();
        while (!eof) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            eof = readLine(stream, baos);
            String line = new String(baos.toByteArray(),Charset.forName("UTF-8"));
            if (firstLine) {
                if (line.startsWith("\uFEFF")) {
                    //remove optional BOM
                    line = line.substring(1);
                }
                firstLine = false;
            }
            if (line.length() == 0) {
                //dispatch event
                StringBuilder dataBuffer = eventBuilder.getDataBuffer();
                int dataLen = dataBuffer.length();
                if (dataLen > 0) {
                    String data;
                    if (dataBuffer.charAt(dataLen - 1) == '\n') {
                        data = dataBuffer.substring(0, dataLen - 1);
                    } else {
                        data = dataBuffer.toString();
                    }
                    Event event = new Event(eventBuilder.getType(),
                            data,
                            eventBuilder.getLastEventId());
                    processEvent(event);
                }
                eventBuilder.reset();
            } else if (!line.contains(":")) {
                processField(line, "", eventBuilder);
            } else if (line.startsWith(":")) {
                //ignore (comment)
            } else {
                String[] parts = line.split(":",2);
                if (parts[1].startsWith(" ")) {
                    parts[1] = parts[1].substring(1);
                }
                processField(parts[0],parts[1],eventBuilder);
            }
        }
    }

    private static void processField(String field, String value, EventBuilder eventBuilder) {
        switch (field) {
            case "event":
                eventBuilder.setType(value);
                break;
            case "data":
                eventBuilder.appendData(value);
                break;
            case "id":
                eventBuilder.setLastEventId(value);
                break;
            case "retry":
                // TODO: Handle retries
                break;
            default:
                break;
        }
    }

    private static void processEvent(Event event) {
        if ("put".equals(event.getType())) {
            SolrServer server = new HttpSolrServer(SOLR_INSTANCE);
            try {
                ObjectNode json = mapper.readValue(event.getData(), ObjectNode.class);
                List<SolrInputDocument> docs = new ArrayList<>();
                for (JsonNode n : json.path("data").path("profiles")) {
                    String userName = n.asText();
                    SolrInputDocument doc = fetchUser(userName);
                    if (doc != null) {
                        docs.add(doc);
                    }
                }
                if (docs.size() > 0) {
                    if (log.isInfoEnabled()) {
                        List<String> ids = new ArrayList<>();
                        for (SolrInputDocument d : docs) {
                            ids.add((String)d.getFieldValue("id"));
                        }
                        log.info("Adding/Updating user(s): {}", StringUtils.join(ids, ", "));
                    }
                    server.add(docs);
                    server.commit();
                }
            } catch (JsonMappingException e) {
                log.warn("Can't parse payload", e);
            } catch (JsonParseException e) {
                log.warn("Can't parse payload", e);
            } catch (IOException e) {
                log.warn("Can't parse payload", e);
            } catch (SolrServerException e) {
                log.warn("Problem indexing profiles", e);
            } finally {
                server.shutdown();
            }
        }
    }

    private static SolrInputDocument fetchUser(String userName) {
        CloseableHttpClient httpclient = HttpClients.createDefault();
        String hnUserUrl = HN_USER_URL_TEMPLATE.replace("${userName}",userName);
        HttpGet httpGet = new HttpGet(hnUserUrl);
        try (CloseableHttpResponse response = httpclient.execute(httpGet)) {
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode >= 200 && statusCode < 300) {
                HttpEntity entity = response.getEntity();
                ObjectNode userJson = mapper.readValue(entity.getContent(), ObjectNode.class);
                SolrInputDocument doc = new SolrInputDocument();
                doc.addField("id", userJson.path("id").asText());
                doc.addField("delay", userJson.path("delay").asInt());
                doc.addField("created", userJson.path("created").asInt());
                doc.addField("karma", userJson.path("karma").asInt());
                doc.addField("about", userJson.path("about").asText());
                for (JsonNode submitted : userJson.path("submitted")) {
                    doc.addField("submitted", submitted.asInt());
                }
                EntityUtils.consume(entity);
                return doc;
            } else {
                log.warn("Problem fetching profile for user {}: {}",userName, response.getStatusLine());
            }
        } catch (IOException e) {
            log.warn("Problem fetching profile for user " + userName, e);
        }
        return null;
    }
}