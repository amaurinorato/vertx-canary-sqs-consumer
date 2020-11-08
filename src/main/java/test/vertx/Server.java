package test.vertx;

import java.io.IOException;

import javax.annotation.PostConstruct;

import com.launchdarkly.sdk.LDUser;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.LDClient;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import uy.kohesive.vertx.sqs.SqsQueueConsumerVerticle;

public class Server {
	
	private static final String FEATURE_FLAG_NAME = "YOUR-FEATURE-FLAG-NAME";
	private static final String AWS_SECRET_KEY = "YOUR-SECRET-KEY";
	private static final String AWS_ACCESS_KEY = "YOUR-ACCESS-KEY";
	private static final String AWS_REGION = "us-east-1";
	private static final String CHANNEL = "channel";
	private static final String SDK_KEY = "PUT_YOUR_LAUNCH_DARKLY_SDK_KEY_HERE";
	private static final String MAIN_QUEUE_URL = "http://localhost:9324/queue/default";
	private static final String CANARY_QUEUE_URL = "http://localhost:9324/queue/default"; //I'm pointing both queues URL to the same endpoint, but the should be different

	Vertx vertx = Vertx.vertx();
	
    private static Logger LOG = LoggerFactory.getLogger(Server.class);
    
	@PostConstruct
    public void init() throws IOException {
        boolean isCanary = isCanary();
        
        LOG.info("Is Canary: " + isCanary);
        
        JsonObject config = getConfig(isCanary ? CANARY_QUEUE_URL: MAIN_QUEUE_URL);

        vertx.deployVerticle(SqsQueueConsumerVerticle.class, new DeploymentOptions().setConfig(config), (deployment) -> {
        	
        	if (isCanary && deployment.succeeded()) {
        		
        		LOG.info("Deployment id: " + deployment.result());
        		
        		vertx.setPeriodic(5000, periodicDeploymentId -> {
        			
        			boolean isStillCanary = false;
					
        	        try {
						isStillCanary = isCanary();
					} catch (IOException e) {
						isStillCanary = true;
						LOG.info("Could not recover canary flag. Assuming it's still canary.");
					}
        	        
        	        LOG.info("isStillCanary: " + isStillCanary);
        	        
        	        if (!isStillCanary) {
        	        	LOG.info("Not canary anymore");
        	        	LOG.info("Undeploying verticle");
	        			vertx.deploymentIDs().stream().forEach(x -> {
	                		if (x.equals(deployment.result())) {
	                			vertx.undeploy(x);
	                			JsonObject newConfig = getConfig(MAIN_QUEUE_URL); //since it's not canary anymore, setting the queue URL to the main queue URL
	                			LOG.info("Deploying verticle poiting to main queue");
	                			vertx.deployVerticle(SqsQueueConsumerVerticle.class, new DeploymentOptions().setConfig(newConfig));
	                			LOG.info("Cancelling the preiodic");
	                			vertx.cancelTimer(periodicDeploymentId);
	                			LOG.info("Periodic cancelled");
	                		}
	                	});
        	        }
                });
        	}
        });

        eventBusConsumer();
    }

	private void eventBusConsumer() {
		vertx.eventBus().consumer(CHANNEL, (message) -> {
            try {
                LOG.info("Message received: " + message.body().toString());
            } catch (Exception e) {
            	e.printStackTrace();
                LOG.error("Could not read message from queue", e);   
            }
            message.reply(null);
        });
	}

	private JsonObject getConfig(String queueURL) {
		JsonObject newConfig = new JsonObject()
		        .put("region", AWS_REGION)
		        .put("pollingInterval", 20)
		        .put("queueUrl", queueURL) 
		        .put("address", CHANNEL)
		        .put("accessKey", AWS_ACCESS_KEY)
		        .put("secretKey", AWS_SECRET_KEY);
		return newConfig;
	}
	
	private boolean isCanary() throws IOException {
		LDClient ldClient = new LDClient(SDK_KEY);
        LDUser user = new LDUser.Builder("UNIQUE IDENTIFIER")
                .firstName("Teste")
                .lastName("Souza")
                .custom(FEATURE_FLAG_NAME, LDValue.buildArray().add("bla").build())
                .build();
        boolean isCanary = ldClient.boolVariationDetail(FEATURE_FLAG_NAME, user, true).getValue(); //DEFAULT TRUE, TO AVOID READING FROM MAIN QUEUE 
        ldClient.close();
        return isCanary;
	}
}
