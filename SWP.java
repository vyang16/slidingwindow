/*===============================================================*
 *  File: SWP.java                                               *
 *                                                               *
 *  This class implements the sliding window protocol            *
 *  Used by VMach class					         *
 *  Uses the following classes: SWE, Packet, PFrame, PEvent,     *
 *                                                               *
 *  Author: Professor SUN Chengzheng                             *
 *          School of Computer Engineering                       *
 *          Nanyang Technological University                     *
 *          Singapore 639798                                     *
 *===============================================================*/
import java.util.Timer;
import java.util.TimerTask;
public class SWP {

/*========================================================================*
 the following are provided, do not change them!!
 *========================================================================*/
   //the following are protocol constants.
  public static final int MAX_SEQ = 7; 
  public static final int NR_BUFS = (MAX_SEQ + 1)/2; //4

  // the following are protocol variables
  private int oldest_frame = 0;
  private PEvent event = new PEvent();  
  private Packet out_buf[] = new Packet[NR_BUFS];

  //the following are used for simulation purpose only
  private SWE swe = null;
  private String sid = null;  

  //Constructor
  public SWP(SWE sw, String s){
    swe = sw;
    sid = s;
  }

  //the following methods are all protocol related
  private void init(){
    for (int i = 0; i < NR_BUFS; i++){
      out_buf[i] = new Packet();
    }
  }

  private void wait_for_event(PEvent e){
    swe.wait_for_event(e); //may be blocked
    oldest_frame = e.seq;  //set timeout frame seq
  }

  private void enable_network_layer(int nr_of_bufs) {
  //network layer is permitted to send if credit is available
    swe.grant_credit(nr_of_bufs);
  }

  private void from_network_layer(Packet p) {
    swe.from_network_layer(p);
  }

  private void to_network_layer(Packet packet) {
    swe.to_network_layer(packet);
  }

  private void to_physical_layer(PFrame fm)  {
    System.out.println("SWP: Sending frame: seq = " + fm.seq + 
                	    " ack = " + fm.ack + " kind = " + 
                	    PFrame.KIND[fm.kind] + " info = " + fm.info.data );
    System.out.flush();
    swe.to_physical_layer(fm);
  }

  private void from_physical_layer(PFrame fm) {
    PFrame fm1 = swe.from_physical_layer(); 
    fm.kind = fm1.kind;
    fm.seq = fm1.seq; 
    fm.ack = fm1.ack;
    fm.info = fm1.info;
  }


/*===========================================================================*
 	implement your Protocol Variables and Methods below: 
 *==========================================================================*/

   //implement selective repeat algorithm

  //system variables
  private int ack_expected = 0;                     //Lower bound sender's window
  private int next_frame_to_send = 0;               //Upper bound sender's window
  private int frame_expected = 0;                   //Lower bound receiver's window
  private int too_far = 0;                          //Upper bound receiver's window 
  private boolean no_nak = true;  
  private Packet in_buf[] = new Packet[NR_BUFS];    
  private boolean arrived[] = new boolean[NR_BUFS]; //Bit Map

  //NR_BUFS timer

  
  public void send_frame(int type, int seqnr, int seqnr_exp, Packet buffer[] ){
    PFrame s = new PFrame();
    s.kind = type;
    s.seq = seqnr;
    s.ack = (seqnr_exp+MAX_SEQ)%(MAX_SEQ+1);
    //can be DATA, ACK or NACK
    if(type == PFrame.DATA){
      s.info = buffer[seqnr%NR_BUFS];
    }else if (type == PFrame.ACK) {
      
    }else if (type == PFrame.NAK){
      no_nak = false;
    }else{
      throw new IllegalArgumentException("Type of the frame is not valid");
    }
    to_physical_layer(s);
    if(type ==PFrame.DATA){
      start_timer(seqnr);
      stop_ack_timer();
    }
  }

  public void protocol6() {
    init();
    enable_network_layer(40);  //network layer is free
    PFrame r = new PFrame();  //received frame
    PFrame s = new PFrame();  //sending frame
    while(true) {	
      wait_for_event(event);
      switch(event.type) {
        case (PEvent.NETWORK_LAYER_READY):
          //send frame, increase sender window upper bound by one
          from_network_layer(out_buf[next_frame_to_send%NR_BUFS]);
          send_frame(PFrame.DATA, next_frame_to_send, frame_expected, out_buf);
          enable_network_layer(1);
          inc(next_frame_to_send);
          break; 
        case (PEvent.FRAME_ARRIVAL ):
          //check if ACK, then increase sender window lower bound by one
          //if not, buffer frame if frame is in accepting window, 
          from_physical_layer(r);
          to_network_layer(r.info);
          //NEED TO SEND TO NETWORK LAYER IN ORDER
          break;	   
        case (PEvent.CKSUM_ERR):
          break;
        case (PEvent.TIMEOUT): 
          //timeout, send frame again
          break;
        case (PEvent.ACK_TIMEOUT): 
          //ack timeout, no ack arrived, send frame again
          break; 
        default: 
          System.out.println("SWP: undefined event type = " 
                               + event.type); 
          System.out.flush();
      }
    }      
  }

 /* Note: when start_timer() and stop_timer() are called, 
    the "seq" parameter must be the sequence number, rather 
    than the index of the timer array, 
    of the frame associated with this timer, 
   */
  private  Timer retr_timers[] = new Timer[MAX_SEQ];
  private Timer ack_timer = new Timer();


  private void start_timer(int seq) {
   //start timer when package is sent
    stop_timer(seq);
    retr_timers[seq] = new Timer();
    //start timer which induces swe.generate_timeout_event(seq) when up

  }

  private void stop_timer(int seq) {
    try{
      retr_timers[seq].cancel();
    }catch(Exception e){
      System.out.println("Could not stop timer of" + seq);
    }
    
  }

  private void start_ack_timer( ) {
    
  }

  private void stop_ack_timer() {
   
  }

  private void start_aux_timer(int seq){

  }

  private void stop_aux_timer(int seq){

  }

  //checks whether seq number is in accepted window [a, b) or not
  static boolean between(int seq, int a, int b) {
    return ((seq>=a)&&(seq<b)) || ((a<=seq)&&(b<a))||((b<a)&&(seq<=b));
  }
   
}//End of class

/* Note: In class SWE, the following two public methods are available:
   . generate_acktimeout_event() and
   . generate_timeout_event(seqnr).

   To call these two methods (for implementing timers),
   the "swe" object should be referred as follows:
     swe.generate_acktimeout_event(), or
     swe.generate_timeout_event(seqnr).
*/


