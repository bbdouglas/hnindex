(function() {

  function searchError(jqXHR, textStatus, errorThrown) {
    $("#results").text("Error: " + textStatus + ", " + errorThrown);
  }

  function search() {
    function searchSuccess(data, textStatus, jqXHR) {
      var results = $("#results");
      results.empty();
      var prev = $("<a>").addClass("nav-link").text("<");
      if (data.response.start > 0) {
        prev.attr("href","");
        prev.click(function(e) {
          e.preventDefault();
          start -= rows;
          qParams.start = start;
          $.ajax(url,settings);
        });
      }
      var headerContents = $("<span>").text("Total: " + data.response.numFound + 
          ", Start: " + data.response.start);
      var next = $("<a>").addClass("nav-link").text(">");
      if (data.response.start + data.response.docs.length < data.response.numFound) {
        next.attr("href","");
        next.click(function(e) {
          e.preventDefault();
          start += rows;
          qParams.start = start;
          $.ajax(url,settings);
        });
      } 
      var header = $("<p>").append(prev).append(headerContents).append(next);
      results.append(header);
      for (var i = 0; i <  data.response.docs.length; ++i) {
        var profileData = data.response.docs[i];
        var profile = $("<p>");
        var userURL = "https://news.ycombinator.com/user?id=" + profileData.id;
        var profileHeader = $('<span><a href="' + userURL + '" target="_blank">' + 
            profileData.id + "</a> (" + profileData.karma + ")</span><br>");
        profile.append(profileHeader);
        var profileBio = $("<div>").addClass("bio");
        profileBio.html(profileData.about);
        profile.append(profileHeader);
        profile.append(profileBio);
        results.append(profile);
      }
    }

    $("#results").text("Searching...");
    var query = $("#query").val();
    var start = 0;
    var rows = 10;
    var url = "/solr/hnindex/select"
    var qParams = {
      q:"{!edismax df=about}" + query,
      wt:"json",
      fl:"id,karma,about",
      start:start,
      rows:rows,
      sort:"karma desc",
    };
    var settings = {
      data:qParams,
      dataType:"json",
      error:searchError,
      success:searchSuccess,
    };

    if (query.length == 0) {
      $("#results").text("Please input a query string.");
    } else if (query.length > 80) {
      $("#results").text("Exceeded maximum query length (80 characters).");
    } else {
      $.ajax(url,settings);
    }
  }

  $( document ).ready(function() {
    var queryBox = $("#query");
    queryBox.val("");
    queryBox.keypress(function(e) {
      var key = e.which;
      if (key == 13) {
        search();
      }
    });
    $("#search").click(search);
  });
})();
