package my.prac.config;

public class DBException extends Exception {
	 /**
	 * 
	 */
	private static final long serialVersionUID = -1L;
	public DBException(String msg){
         super(msg);
     }       
     public DBException(Exception ex){
         super(ex);
     }
}
