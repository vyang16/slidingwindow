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
  public static final int NR_BUFS = (MAX_SEQ + 1)/2; //window size

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
  private int too_far = NR_BUFS;                          //Upper bound receiver's window
  private boolean no_nak = true;
  private Packet in_buf[] = new Packet[NR_BUFS];
  private boolean arrived[] = new boolean[NR_BUFS]; //Bit Map
  private int nbuffered = 0;


  //b needs to be either [a b c], c] [a b or b c] [a to be in the window
  static boolean between(int a, int b, int c) {
    return (((a<=b) && (b<c)) || ((c<a) && (a<=b)) || ((b<c) && (c<a)));
  }

  public int inc(int frame){
     return ((frame+1)%(MAX_SEQ+1)); //wrap around
  }

  public void send_frame(int type, int seqnr, int seqnr_exp, Packet buffer[] ){
    PFrame s = new PFrame();
    s.kind = type;
    //can be DATA, ACK or NAK
    if(type == PFrame.DATA){
      s.info = buffer[seqnr%NR_BUFS];
    }
    s.seq = seqnr;
    s.ack = (seqnr_exp+MAX_SEQ)%(MAX_SEQ+1);
    if (s.kind == PFrame.NAK){
      no_nak = false;
    }
    to_physical_layer(s);
    if(s.kind ==PFrame.DATA){
      start_timer(seqnr);
    }
    stop_ack_timer();
  }

  public void protocol6() {
    init();

    for(int i=0; i<NR_BUFS; i++){
      arrived[i] = false;
    }

    enable_network_layer(NR_BUFS);  //fill with frames from network layer
    PFrame r = new PFrame();  //received frame
    PFrame s = new PFrame();  //sending frame

    while(true) {
      wait_for_event(event);
      switch(event.type) {
        case (PEvent.NETWORK_LAYER_READY):
          nbuffered++;
          //send frame, increase sender window upper bound by one
          from_network_layer(out_buf[next_frame_to_send%NR_BUFS]);
          send_frame(PFrame.DATA, next_frame_to_send, frame_expected, out_buf);
          next_frame_to_send = inc(next_frame_to_send);
          break;
        case (PEvent.FRAME_ARRIVAL ):
          //check if ACK, then increase sender window lower bound by one
          //if not, buffer frame if frame is in accepting window,
          from_physical_layer(r);
          if(r.kind == PFrame.DATA){
            if((r.seq != frame_expected)&& no_nak){
              //wrong frame, send NAK if we haven't done it yet
              send_frame(PFrame.NAK, 0, frame_expected, out_buf);
            }else{
              start_ack_timer();
            }
            if(between(frame_expected, r.seq, too_far)&&(!arrived[r.seq%NR_BUFS])){
                //buffer frame
                arrived[r.seq%NR_BUFS] = true;
                in_buf[r.seq%NR_BUFS] = r.info;
                //NEED TO SEND TO NETWORK LAYER IN ORDER
                while(arrived[frame_expected%NR_BUFS]){
                  to_network_layer(in_buf[frame_expected%NR_BUFS]);
                  no_nak = true;
                  arrived[frame_expected%NR_BUFS] = false;
                  frame_expected = inc(frame_expected);
                  too_far = inc(too_far);
                  start_ack_timer();
                }
            }
          }
          if((r.kind == PFrame.NAK) && between(ack_expected, (r.ack+1)%(MAX_SEQ+1), next_frame_to_send)){
            send_frame(PFrame.DATA, (r.ack+1)%(MAX_SEQ+1), frame_expected, out_buf);
          }

          while(between(ack_expected, r.ack, next_frame_to_send)){
            nbuffered--;
            stop_timer(ack_expected% NR_BUFS);
            ack_expected = inc(ack_expected);
            enable_network_layer(1); //each time an ack arrives, send the next frame
          }

          break;
        case (PEvent.CKSUM_ERR):
          if(no_nak)
            send_frame(PFrame.NAK, 0, frame_expected, out_buf);
          break;
        case (PEvent.TIMEOUT):
          //timeout, send frame again
          send_frame(PFrame.DATA, oldest_frame, frame_expected, out_buf);
          break;
        case (PEvent.ACK_TIMEOUT):
          //ack timeout, no ack arrived, send ack again
          send_frame(PFrame.ACK, 0, frame_expected, out_buf);
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
  private Timer ack_timer;
  public int TR_DUR = 200; //in ms
  public int ACK_DUR = 50; //in ms

  public class TimeoutTask extends TimerTask{
    private int seq;
    //New constructor
    public TimeoutTask(int n){
      seq = n;
    }

    @Override
    public void run() {
        stop_timer(seq);
        swe.generate_timeout_event(seq);
    }
  }


  private void start_timer(int seq) {
   //start timer when package is sent
    stop_timer(seq);
    retr_timers[seq%NR_BUFS] = new Timer();
    TimerTask timertask = new TimeoutTask(seq);
    retr_timers[seq%NR_BUFS].schedule(timertask, TR_DUR);
  }

  private void stop_timer(int seq) {
    try{
      retr_timers[seq].cancel();
    }catch(Exception e){
      System.out.println("Could not stop timer of" + seq);
    }

  }

  public class ACKTask extends TimerTask{
    @Override
    public void run(){
          stop_ack_timer();
          swe.generate_acktimeout_event();
    }
  }

  private void start_ack_timer( ) {
    stop_ack_timer();
    ack_timer = new Timer();
    TimerTask acktask = new ACKTask();
    ack_timer.schedule(acktask, ACK_DUR);
  }

  private void stop_ack_timer() {
    if(ack_timer != null){
      ack_timer.cancel();
      ack_timer = null;
    }
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
