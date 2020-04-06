package com.rishidua;

import com.google.api.services.youtube.model.SearchListResponse;
import java.io.ByteArrayInputStream;
import java.util.Collections;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;

import java.util.Properties;
import java.util.concurrent.CountDownLatch;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;

import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.PlaylistItem;
import com.google.api.services.youtube.model.PlaylistItemSnippet;
import com.google.api.services.youtube.model.ResourceId;

import java.io.IOException;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.Collection;


/**
 * Listens to "songs-requests" kakfka stream, searches on YouTube to find relevant video and adds to a playlist.
 *
 * YouTube data API is used searching and adding to Playlist. Authentication is done using Oauth.
 * Detailed logs are printed to a logger, a short summary is written to "songs-logs" kafka steam.
 */
public class SongsProcessor {

  private static final String CLIENT_SECRETS = "PASTE_CONTENTS_OF_client_id.json here";
  private static final Collection<String> SCOPES =
      Collections.singletonList("https://www.googleapis.com/auth/youtube.force-ssl");
  private static final String APPLICATION_NAME = "Kakfa Playlist Demo";
  private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
  private static final String SONGS_REQUESTS_STREAM = "songs-requests";
  private static final String SONGS_LOGS_STREAM = "songs-logs";
  public static final int MAX_ATTEMPTS = 3;

  /**
   * Call function to create API service object. Define and
   * execute API request. Print API response.
   *
   * @throws GeneralSecurityException, IOException, GoogleJsonResponseException
   */
  public static void main(String[] args) throws GeneralSecurityException, IOException, GoogleJsonResponseException {

    // Initialize and get YouTube service.
    YouTube youtubeService = createYouTubeService();

    // Set up Kakfa
    Properties props = new Properties();
    props.put(StreamsConfig.APPLICATION_ID_CONFIG, "songs-queue");
    props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
    props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
    props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());

    final StreamsBuilder builder = new StreamsBuilder();
    builder.<String, String>stream(SONGS_REQUESTS_STREAM).mapValues(value -> processStreamInput(youtubeService, value)).to(SONGS_LOGS_STREAM);

    final Topology topology = builder.build();
    final KafkaStreams streams = new KafkaStreams(topology, props);
    final CountDownLatch latch = new CountDownLatch(1);

    // attach shutdown handler to catch control-c
    Runtime.getRuntime().addShutdownHook(new Thread("streams-shutdown-hook") {
      @Override
      public void run() {
        streams.close();
        latch.countDown();
      }
    });

    try {
      streams.start();
      latch.await();
    } catch (Throwable e) {
      System.exit(1);
    }
  }

  /**
   * Build and return an authorized YouTube API client service.
   *
   * @return an authorized API client service
   * @throws GeneralSecurityException, IOException
   */
  private static YouTube createYouTubeService() throws GeneralSecurityException, IOException {
    final NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
    // Create an authorized Credential object.
    GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY,
        new InputStreamReader(new ByteArrayInputStream(CLIENT_SECRETS.getBytes())));
    // Build flow and trigger user authorization request.
    GoogleAuthorizationCodeFlow flow =
        new GoogleAuthorizationCodeFlow.Builder(httpTransport, JSON_FACTORY, clientSecrets, SCOPES).build();
    Credential credential = new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
    return new YouTube.Builder(httpTransport, JSON_FACTORY, credential).setApplicationName(APPLICATION_NAME).build();
  }

  /**
   * Processes input from requests steream and return a summary response for adding to logs stream.
   */
  private static String processStreamInput(YouTube youtubeService, String input) {
    String responseSummary;
    System.out.println("Searching for videos related to: " + input);
    String videoId = null;
    try {
      videoId = getYTVideo(youtubeService, input);
    } catch (IOException e) {
      responseSummary = "Failed to get video for input " + input + "Exception: " + e;
      return responseSummary;
    }
    System.out.println("Found video: " + videoId + "Now adding to playlist");
    try {
      if (addSongToPlayList(youtubeService, videoId)) {
        responseSummary = "Successfully Added " + videoId + "for input " + input;
      } else {
        responseSummary = "Failed to add " + videoId + "for input " + input + "due to 302 errors";
      }
    } catch (Exception e) {
      responseSummary = "Got exception: " + e + " while trying to add " + videoId + "for input " + input;
    }
    System.out.println(responseSummary);
    return responseSummary;
  }

  /** Finds YT video with closest match for input. **/
  private static String getYTVideo(YouTube youtubeService, String input) throws IOException {
    YouTube.Search.List request =
        youtubeService.search().list("snippet").setMaxResults(2L).setType("video").setQ(input);
    SearchListResponse response = request.execute();
    String videoId = response.getItems().get(0).getId().getVideoId();
    return videoId;
  }

  /**
   * Adds video to playlist. Returning whether it was successful.
   */
  private static boolean addSongToPlayList(YouTube youtubeService, String videoId) throws IOException {
    // Define the PlaylistItem object, which will be uploaded as the request body.
    PlaylistItem playlistItem = new PlaylistItem();

    // Add the snippet object property to the PlaylistItem object.
    PlaylistItemSnippet snippet = new PlaylistItemSnippet();
    snippet.setPlaylistId("PLupERWZDlxBWuqcvpyIQGFmu7mtpl6-Kf");
    snippet.setPosition(0L);
    ResourceId resourceId = new ResourceId();
    resourceId.setKind("youtube#video");
    resourceId.setVideoId(videoId);
    snippet.setResourceId(resourceId);
    playlistItem.setSnippet(snippet);

    // Define and execute the API request
    YouTube.PlaylistItems.Insert request = youtubeService.playlistItems().insert("snippet", playlistItem);

    // For some reason, we are getting 302 erros from YouTube. Try upto 3 times, this should work as the error is very rare.
    PlaylistItem response = null;
    for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
      try {
        response = request.execute();
        break; // Do not add to Playlist again, if an attempt succeeds.
      } catch (GoogleJsonResponseException e) {
        if (302 == e.getStatusCode()) {
          System.out.println("Received 302 from YouTube in attempt: " + attempt + "\nRetrying...");
        }
        else throw e;
      }
    }
    return response != null;
  }
}
