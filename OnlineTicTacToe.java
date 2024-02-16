import com.jcraft.jsch.*;
import java.util.Scanner;
import java.io.Console;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.lang.*;

import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JOptionPane;

/**
 *
 * @author Greeshma Sree Parimi
 */
public class OnlineTicTacToe implements ActionListener {

    private final int INTERVAL = 1000;        // timeout 
    private final int NBUTTONS = 9;            // Number of bottons on game board
    private ObjectInputStream input = null;    // input from my counterpart
    private ObjectOutputStream output = null;  // output from my counterpart
    private JFrame window = null;              // the tic-tac-toe game window
    private JButton[] button = new JButton[NBUTTONS]; // button[0] - button[9]
    private boolean[] myTurn = new boolean[1]; // T: my turn, F: your turn
    private String myMark = null;              // "O" or "X"
    private String yourMark = null;            // "X" or "O"
  	private Socket client = null;			   // socket
  	private int NumberOfMovesLeft = 0;		   // total Number of moves
  	private boolean isAutoPlayer = false;	   // Flag for auto player
    /** markedArray to store player's choices at each index */
	private String[] markedArray = new String[NBUTTONS]; 
	/** logs to write into log file*/
	private PrintWriter logs = null;			

    /**
     * Prints out the usage.
     */
    private static void usage( ) {
        System.err.
	    println( "Usage: java OnlineTicTacToe ipAddr ipPort(>=5000) [auto]" );
        System.exit( -1 );
    }

    /**
     * Prints out the track trace upon a given error and quits the application.
     * @param an exception 
     */
    private static void error( Exception e ) {
        e.printStackTrace();
        System.exit(-1);
    }

    /**
     * Starts the online tic-tac-toe game.
     * @param args[0]: my counterpart's ip address, args[1]: his/her port, (arg[2]: "auto")
     *        if args.length == 0, this Java program is remotely launched by JSCH.
     */
    public static void main( String[] args ) {

		if ( args.length == 0 ) {
			// if no arguments, this process was launched through JSCH
			try {
				OnlineTicTacToe game = new OnlineTicTacToe( );
			} catch( IOException e ) {
				error( e );
			}
		}
		else {
			// this process wa launched from the user console.

			// verify the number of arguments
			if ( args.length != 2 && args.length != 3 ) {
				System.err.println( "args.length = " + args.length );
				usage( );
			}

			// verify the correctness of my counterpart address
			InetAddress addr = null;
			try {
				if(args[0].equals("localhost")){
					addr = InetAddress.getLocalHost();
				}else{
					addr = InetAddress.getByName( args[0] );
				}
			} catch ( UnknownHostException e ) {
				error( e );
			}
			
			// verify the correctness of my counterpart port
			int port = 0;
			try {
				port = Integer.parseInt( args[1] );
			} catch (NumberFormatException e) {
				error( e );
			}
			if ( port < 5000 ) {
				usage( );
			}
			
			// check args[2] == "auto"
			if ( args.length == 3 && args[2].equals( "auto" ) ) {
				// auto play
				OnlineTicTacToe game = new OnlineTicTacToe( args[0] );
			}
			else { 
				// interactive play
				OnlineTicTacToe game = new OnlineTicTacToe( addr, port );
			}
		}
    }

    /**
     * Is the constructor that is remote invoked by JSCH. It behaves as a server.
     * The constructor uses a Connection object for communication with the client.
     * It always assumes that the client plays first. 
     */
    public OnlineTicTacToe( ) throws IOException { // remote server
		// receive an ssh2 connection from a user-local master server.
		Connection connection = new Connection( );
		input = connection.in;
		output = connection.out;

		// for debugging, always good to write debugging messages to the local file
		// don't use System.out that is a connection back to the client.

		logs = new PrintWriter( new FileOutputStream( "logs.txt" ));
		logs.println( "Autoplay: got started." );
		logs.flush( );

		myMark = "X";   // auto player is always the 2nd.
		yourMark = "O"; // user local system

		myTurn[0]= false;
		
		int markedNumber = -1;
		NumberOfMovesLeft = 9;
		isAutoPlayer = true;

		// start my counterpart thread to read data
		Counterpart counterpart = new Counterpart( );
		counterpart.start();

		/** this is to choose the  auto player's
		 * next move. It synchrnoziez using 
		 * myTurn object.
		*/  
		while (true) {
			synchronized(myTurn){
				/**If it's not auto player's turn
				 * it waits for its turn
				 */
				while (!myTurn[0]) {
					try {
						myTurn.wait();
					} catch (Exception e) {
						error(e);
					}
				}
				try{
				/** Write the next move to the real user 
				 * and update markedArray and NumberOfMovesLeft accordingly
				 * Also checks if auto player won the game or
				 * game ended in a tie.*/
					int nextMove = chooseNextMove(markedArray,myMark,yourMark);
					if(myTurn[0]){
						markedArray[nextMove] = myMark;
						output.writeInt(nextMove);
						output.flush();
						myTurn[0]=false;
						NumberOfMovesLeft--;
						logs.println("Auto player marked at "+ nextMove); 
						logs.flush();
						boolean doesWin = checkWinOrBlock(markedArray,myMark);
						if(doesWin){
							logs.println("Auto player won");
							logs.flush();
							break;
						}
						if(!doesWin && NumberOfMovesLeft <= 0){
							logs.println("Its a tie");
							logs.flush();
							break;
						}
						myTurn.notify();
					}
				}catch(Exception e){
					logs.println("An exception occured: " + e);
					logs.flush();
				}
				

			}		
			
		}
    }

    /**
     * Is the constructor that, upon receiving the "auto" option,
     * launches a remote OnlineTicTacToe through JSCH. This
     * constructor always assumes that the local user should play
     * first. The constructor uses a Connection object for
     * communicating with the remote process.
     *
     * @param my auto counter part's ip address
     */
    public OnlineTicTacToe( String hostname ) { // to connect to remote server
        final int JschPort = 22;      // Jsch IP port

        /** Read username, password, 
		 * and a remote host from keyboard */ 
        Scanner keyboard = new Scanner( System.in );
        String username = null;
        String password = null;
		try {
            /** read the user name from the console 
			 * to connect to remote server  */                                                           
            System.out.print( "User: " );
            username = keyboard.nextLine( );

            /** read the password from the console to 
			 * connect to remote server */                                                           
            Console console = System.console( );
            password = new String( console.readPassword( "Password: " ) );

        } catch( Exception e ) {
            error(e);
        }

        /** establishing an ssh2 connection 
		 * to remote system ip and run server there */ 
		String cur_dir = System.getProperty( "user.dir" );
		String command 
			= "java -cp " + cur_dir + "/jsch-0.1.54.jar:" + cur_dir + 
			" OnlineTicTacToe";
		
		Connection connection = new Connection( username, password,
							hostname, command );
		
		/** input and output streams to read 
		 * and write data to counterpart */ 
		input = connection.in;
		output = connection.out;
		

		//set up a game window
		makeWindow( true ); // I'm a former
		
		// setting number of moves to 9
		NumberOfMovesLeft = 9;
		
		// start my counterpart thread
		Counterpart counterpart = new Counterpart( );
		counterpart.start();
    }

    /**
     * Is the constructor that sets up a TCP connection 
	 * with my counterpart,brings up a game window, 
	 * and starts a slave thread for listenning to
     * my counterpart.
     * @param my counterpart's ip address
     * @param my counterpart's port
     */
    public OnlineTicTacToe( InetAddress addr, int port ) {
        // setting up a TCP connection with my counterpart
		ServerSocket server = null;
		client = null;
		boolean isAServer = false;
		InetAddress localIpAddress = null;
		try{
			// this ic the local system name 
			localIpAddress = InetAddress.getLocalHost();
		}catch(Exception e){

		}
        try {
			//set the server non-blocking
            server = new ServerSocket( port );
            server.setSoTimeout(INTERVAL);
        } catch ( Exception e ) {	
			try{
				/** handling bind exception when connecting
				 * from two terminals from same system case.
				 * Other system just acts as client after facing
				 * address alreday bind exception*/
				if(client == null){
					client =  new Socket(addr, port);			
				}
	    	}catch(Exception ex){	
				error(e);
	    	}
        }

        /** While accepting a remote request, 
		 * try to send my connection request */ 
        while ( true ) {
            try {
				if(client != null)break;

                client = server.accept();
				/** check if the connection got established. 
				 * If so, leave the loop */
				if (client != null ){ 
		    		isAServer = true;
		    		break;
				}
            } catch ( SocketTimeoutException ste ) {
				// Couldn't receive a connection request withtin INTERVAL
	    	} catch ( IOException ioe ) {
				error( ioe );
	   		}catch(Exception e){

	    	}
			/**In same system use case, this restricts 
			 * the server to not turn itself to client
			  */
	    	if(addr.equals(localIpAddress)){
				continue;
			}
			try{
				// Try to request a connection as a client
				if(client == null){
					client =  new Socket(addr, port);
				}
			}catch (Exception e) {

			}
			
			/** check if the connection got established. 
			 * If so, leave the loop */
			if(client != null){
				isAServer = false;
				break; 
			} 	
        }

	
      
        System.out.println( "TCP connection established..." );

		/** set up game window based on isAServer flag, 
		 * if server it will be latter(X) otherwise former(O). */ 
		makeWindow(!isAServer); 
        
		// setting number of moves to 9
		NumberOfMovesLeft = 9; 
		
		/** input and output streams for transfering 
		 * the data to and from the counterpart. */ 
		try{
			output = new ObjectOutputStream(client.getOutputStream());
			input = new ObjectInputStream(client.getInputStream());
		}catch(Exception e){
			error(e);
		}

		// start my counterpart thread
        Counterpart counterpart = new Counterpart( );
        counterpart.start();
    }

    /**
     * Creates a 3x3 window for the tic-tac-toe game
     * @param true if this window is created by the former, (i.e., the
     *        person who starts first. Otherwise false.
     */
    private void makeWindow( boolean amFormer ) {
        myTurn[0] = amFormer;

        myMark = ( amFormer ) ? "O" : "X";    // 1st person uses "O"
        yourMark = ( amFormer ) ? "X" : "O";  // 2nd person uses "X"
	    System.out.println("MY MARK " + myMark);
        // create a window
        window = new JFrame("OnlineTicTacToe(" +
                ((amFormer) ? "former)" : "latter)" ) + myMark );
        window.setSize(300, 300);
	    window.setLocation(20, 20);
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        window.setLayout(new GridLayout(3, 3));

		// initialize all nine cells.
        for (int i = 0; i < NBUTTONS; i++) {
            button[i] = new JButton();
            window.add(button[i]);
            button[i].addActionListener(this);
        }

		// make it visible
        window.setVisible(true);
    }

    /**
     * Marks the i-th button with mark ("O" or "X")
     * @param the i-th button
     * @param a mark ( "O" or "X" )
     * @param true if it has been marked in success
     */
    private boolean markButton( int i, String mark ) {
		if ( button[i].getText( ).equals( "" ) ) {
			button[i].setText( mark );
			button[i].setEnabled( false );
			return true;
		}
		return false;
    }

    /**
     * Checks which button has been clicked
     * @param an event passed from AWT 
     * @return an integer (0 through to 8) that shows 
	 * which button has been 
     * clicked. -1 upon an error. 
     */
    private int whichButtonClicked( ActionEvent event ) {
		for ( int i = 0; i < NBUTTONS; i++ ) {
			if ( event.getSource( ) == button[i] )
			return i;
		}
		return -1;
    }

    /**
     * Checks if the i-th button has been marked with mark( "O" or "X" ).
     * @param the i-th button
     * @param a mark ( "O" or "X" )
     * @return true if the i-th button has been marked with mark.
     */
    private boolean buttonMarkedWith( int i, String mark ) {
		return button[i].getText( ).equals( mark );
    }

    /**
     * Pops out another small window indicating that mark("O" or "X") won!
     * @param a mark ( "O" or "X" )
     */
    private void showWon( String mark ) {
		JOptionPane.showMessageDialog( null, mark + " won!" );	
    }

	/**
     * Pops out another small window indicating game is tied.
     */
    private void showTie() {
		JOptionPane.showMessageDialog( null, "It's a tie" );	
    }

    /**
     * Is called by AWT whenever any button has been clicked. You have to:
     * <ol>
     * <li> check if it is my turn,
     * <li> check which button was clicked with whichButtonClicked( event ),
     * <li> mark the corresponding button with markButton( buttonId, mark ),
     * <li> send this informatioin to my counterpart,
     * <li> checks if the game was completed with 
     *      buttonMarkedWith( buttonId, mark ) 
     * <li> shows a winning message with showWon( )
     */
    public  void actionPerformed( ActionEvent event ) {
	
		if(!myTurn[0]){
			System.out.println("Not My Turn");
			return;
		}
		synchronized(myTurn){
			// If its not my turn, then wait till gets notified its myturn
			while (!myTurn[0]) {
				try {
					myTurn.wait();
				} catch (Exception e) {
					error(e);
				}
			}

			/** identifies which button user clicked 
			 * and marks the button with users mark */  
			int buttonIndex = whichButtonClicked(event);
			boolean isButtonMarked = markButton(buttonIndex, myMark);

			// writes the users move to counterpart player.
			try {
				output.writeInt(buttonIndex);
				output.flush();
				NumberOfMovesLeft--;
				System.out.println("My Move is at " + buttonIndex);
			} catch (Exception e) {
				error(e);
			}
			// checks if the user won the game by recent move.
			boolean doesWin = checkForWin(myMark);
			if (doesWin) {
				// displays a dialog box indicating that user won.
				System.out.println("User won");
				showWon(myMark);
				try{
					if(client != null){
						client.close();
					}
				}catch(Exception e){
					error(e);
				}
				
			}
			
			/** if NumberOfMovesLeft is less than or 
			 * equal to zero then game is over.
			 * shows a dialog stating game tied.*/ 
			
			if(!doesWin && NumberOfMovesLeft <= 0) {
				System.out.println("No moves left");
				showTie();
				try{
					if(client != null){
						client.close();
					}
				}catch(Exception e){
					error(e);
				}
			}
			/** make myTurn false after the move 
			 * and notify counterpart that it's ther turn. */ 
			myTurn[0] = false;
			myTurn.notify();
		}
   }

    /**
     * This is a reader thread that keeps reading form and behaving as my
     * counterpart.
     */
    private class Counterpart extends Thread {

	/**
	 * Is the body of the Counterpart thread.
	 */
        @Override
        public void run( ) {
			while(true){
				/**if it is my turn then wait, 
				 * till other person makes a move to read*/ 
				synchronized(myTurn){
					while (myTurn[0]) {
						try {
							myTurn.wait();
							
						} catch (Exception e) {
							error(e);
						}
					}

					try{
						/** reads the button index clicked
						 *  by counter part  and marks 
						 * the button on the game board */ 
						int buttonIndex = input.readInt();
						if(!isAutoPlayer){
							boolean isButtonMarked = markButton(buttonIndex,yourMark);
							System.out.println("Counterpart Move is at " + buttonIndex);
							NumberOfMovesLeft--;
							/**checks if the counter part won 
							 * the game by recent move. */ 
							boolean doesWin = checkForWin(yourMark);
							if(doesWin){
								/** displays a dialog box indicating 
								that counterpart won.*/
								System.out.println("Counterpart won !");
								showWon(yourMark);
								try{
									if(client != null){
										client.close();
									}
								}catch(Exception e){
									error(e);
								}
								break;
							}
						
							/** if NumberOfMovesLeft is less than or 
							 * equal to zero then game is over.
							 * shows a dialog stating game tied.*/ 
							if(!doesWin && NumberOfMovesLeft <= 0) {
								System.out.println("No moves left");
								showTie();
								try{
									if(client != null){
										client.close();
									}
								}catch(Exception e){
									error(e);
								}
								break;
							}
						}else{
							/** This code stores user markings in an
							 * array for auto player to make choices
							 * and also checks if local user(counterpart of
							 * remote player) won or game is a tie.
							 */
							markedArray[buttonIndex] = yourMark;
							NumberOfMovesLeft--;
							logs.println("counter part marked at " + buttonIndex);
							logs.flush();
							boolean doesWin = checkWinOrBlock(markedArray,yourMark);
							if(doesWin){
								logs.println("Counter part won");
								logs.flush();
								break;
							}
							if(!doesWin && NumberOfMovesLeft <= 0){
								logs.println("Its a tie");
								logs.flush();
								break;
							}
						}						
						/** Make myTurn true, as it is current user's turn 
						 * and notify the user. */
						myTurn[0]= true;
						myTurn.notify();

				
					}catch(Exception e){
						error(e);
					}
				}
	    	}
		}
	}

	/**
     * This method is used to check if player won.
	 * @param mark (either "O" or "X") as input to check 
	 * which player won.
	 * It return true if player with mark won
	 * return false, if not.
     */
    private boolean checkForWin(String mark){
		// winning combinations based on button indexes.
		int[][] winCombinations = {{0,1,2},{3,4,5},{6,7,8},
									{0,3,6},{1,4,7},{2,5,8},
									{2,4,6},{0,4,8}};

		for(int[] combination : winCombinations){
			if(buttonMarkedWith(combination[0],mark) && 
			buttonMarkedWith(combination[1],mark) && 
			buttonMarkedWith(combination[2],mark)){
				return true;
			}
			
		}
		return false;
    }

	/**
     * This method is called by auto player constructor to choose next move.
	 * This method checks if by a move auto player can win
	 * if else it checks if by a move auto player can 
	 * block the winning of local user
	 * Other wise it chooses a random free button number and sent back.
	 * @param markedArray (array containing markings of two players till then)
	 * @param myMark (either "O" or "X")
	 * @param yourMark (either "O" or "X")
     */
	private int chooseNextMove(String[] markedArray, String myMark,String yourMark){
		// To choose a winning move for autoplayer
		for(int i=0;i<markedArray.length;i++){
			if(markedArray[i] == null){
				markedArray[i] = myMark;
				boolean moveForWin = checkWinOrBlock(markedArray, myMark);
				markedArray[i] = null;
				if(moveForWin){
					return i;
				}
			}
		}
		/** To choose a blocking move which can 
		 * stop counterpart from winning*/ 
		for(int i=0;i<markedArray.length;i++){
			if(markedArray[i] == null){
				markedArray[i] = yourMark;
				boolean moveForBlock = checkWinOrBlock(markedArray,yourMark);
				markedArray[i] = null;
				if(moveForBlock){
					return i;
				}
			}
		}
		/** If no winning or blocking move is found, 
		 * auto player chooses free random button. */ 
		int move = -1;
		do{
			move = (int)(Math.random()*9);
		}while(markedArray[move] != null);
		return move;

	}

	/**
     * This method is to check if player can win or block by a move
	 * @param markedArray (array containing markings of two players till then)
	 * @param mark (either "O" or "X")
	 * returns true if it can win or block
	 * else false
     */
	private boolean checkWinOrBlock(String[] markedArray, String mark){
        // combinations of indexes of board where a player can win
        int[][] combinations = {{0,1,2},{3,4,5},{6,7,8},
                                {0,3,6},{1,4,7},{2,5,8},
                                {2,4,6},{0,4,8}};
        for(int[] combination : combinations){
            int i = combination[0];
            int j = combination[1];
            int k = combination[2];
            if((mark.equals(markedArray[i])) &&
            (mark.equals(markedArray[j])) &&
            (mark.equals(markedArray[k]))){
                return true;
            }
        }
        return false;
    }
} 