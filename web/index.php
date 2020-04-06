<?php
ini_set('display_errors', 1);
ini_set('display_startup_errors', 1);
error_reporting(E_ALL);
if(isset($_POST["submit"])) {
  if($_POST['song'] != "")
  {
    $entry = $_POST['song'];
    $output  = shell_exec ("echo $entry | /Users/rdua/kafka/bin/kafka-console-producer.sh --broker-list localhost:9092 --topic songs-requests 2>&1");
  }
}
?>

<!DOCTYPE html>
<html>
<head>
  <!-- Latest compiled and minified CSS -->
  <link rel="stylesheet" href="https://stackpath.bootstrapcdn.com/bootstrap/3.4.1/css/bootstrap.min.css" integrity="sha384-HSMxcRTRxnN+Bdg0JdbxYKrThecOKuH5zCYotlSAcp1+c8xmyTe9GYg1l9a69psu" crossorigin="anonymous">

  <!-- Optional theme -->
  <link rel="stylesheet" href="https://stackpath.bootstrapcdn.com/bootstrap/3.4.1/css/bootstrap-theme.min.css" integrity="sha384-6pzBo3FDv/PJ8r2KRkGHifhEocL+1X2rVCTTkUfGk7/0pbek5mMa1upzvWbrUbOZ" crossorigin="anonymous">

  <!-- Latest compiled and minified JavaScript -->
  <script src="https://stackpath.bootstrapcdn.com/bootstrap/3.4.1/js/bootstrap.min.js" integrity="sha384-aJ21OjlMXNL5UyIl/XNwTMqvzeRMZH2w8c5cRVpzpU8Y5bApTppSuUkhZXN0VxHd" crossorigin="anonymous"></script>
</head>
<body>
  <div class="container">
   <h2>Welcome Home</h2>
   <h3>Add song to playlist </h3>
   <p>No YouTube login needed</p>
   <p>No WiFi access needed</p>
   <form action="index.php" class="form-inline" method="post" enctype="multipart/form-data">
    <div class="form-group">
      <label for="song">What do you want to listen?</label>
      <input id="song" type="text"  class="form-control" name="song" value="" required=""><br>
      <button type="submit"   class="btn btn-primary" value="Add to playlist" name="submit" >Add to playlist</button>
    </div>
  </form>
</div>
</body>
</html>