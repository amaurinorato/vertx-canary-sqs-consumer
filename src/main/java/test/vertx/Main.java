package test.vertx;

import java.io.IOException;

public class Main {
    
	public static void main(String[] args) {
		Server server = new Server();
		try {
			server.init();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
}
