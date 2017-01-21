# FTP_Library
FRC Team 2706's FTP Library for our scouting app.

# Usage:
----------
FTPClient client = new FTPClient(String IPAddr, String Username, String Password, String LocalDir, [int port]);
IPAddr: the IP Adress of the server you want to connect to.
Username & Password: Login credentials for the FTP Server.
LocalDir: The directory on your device there the files will be saved to.
port: [Optional] The port to connect to te server on.
