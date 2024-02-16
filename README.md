This repository hosts a Java-based Tic-Tac-Toe game that offers multiple ways to play and employs various technologies for connectivity and gameplay:

Gameplay Modes:

1. Local Play with Automatic Player: Players can engage in a local game where an automatic player acts as the opponent. The automatic player's moves are determined by an algorithm, providing a challenging experience. Users take turns playing against the automatic player on the same machine.
  
2. Remote Play with JSCH Integration: Users can connect to a remote system and play Tic-Tac-Toe against a remote opponent.JSCH integration ensures secure communication between the local and remote systems. Authentication is handled via username and password input from the console. A game window opens for the local user, enabling interactive gameplay with the remote opponent.

3. TCP-Based Play Between Systems or Terminals: Players can establish a TCP connection between two different systems or terminals. The game sets up a server socket to listen for incoming connections on a specified port. If no client connects within a timeout, the program attempts to establish a client socket to connect to a specified IP address and port. Once connected, a game window opens for each player, allowing them to play Tic-Tac-Toe in real-time.


Technologies & algorithms Used:

1. Java Programming Language
2. JSCH (Java Secure Channel): Used for secure communication between the local and remote systems in the remote play mode.Enables encrypted data transfer and authentication for a secure gaming experience.
3. TCP/IP Networking: Utilized for establishing connections between different systems or terminals. TCP sockets facilitate reliable data transfer and communication between players.
4. Algorithm for Automatic Player: Employs an algorithm to determine the automatic player's moves during local play. The algorithms first analyses winning move of the remote player, if it does not exists it analyse winning move of oponent or else it will pick random move.
