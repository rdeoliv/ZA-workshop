package io.confluent.examples.datacontract;

import com.github.javafaker.Faker;
import io.confluent.examples.datacontract.datagen.SalesDataGen;
import io.confluent.examples.datacontract.utils.ClientsUtils;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.log4j.Logger;

import java.util.Properties;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ProducerApp implements Runnable {

    private static final Logger logger = Logger.getLogger(ProducerApp.class);

    private Properties props;
    private String topic, dlq;

    ProducerApp(
            String propertiesFile,
            String clientId) {
        try {
            props = ClientsUtils.loadConfig(propertiesFile);
            if (clientId != null) {
                props.put(ProducerConfig.CLIENT_ID_CONFIG, clientId);
            }
            props.put(AbstractKafkaSchemaSerDeConfig.AUTO_REGISTER_SCHEMAS, "false");
            props.put(AbstractKafkaSchemaSerDeConfig.USE_LATEST_VERSION, "true");
            props.put(AbstractKafkaSchemaSerDeConfig.LATEST_COMPATIBILITY_STRICT, "false");

            // Refresh schema cache every 5 seconds
//            props.put(AbstractKafkaSchemaSerDeConfig.LATEST_CACHE_TTL, 1000);
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

            props.put("value.serializer", "io.confluent.kafka.serializers.KafkaAvroSerializer");
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("Error in ProducerApp.constructor: " + e);
        }
    }

    @Override
    public void run() {
        topic = "payments";
        Random random = new Random();
        try (Producer<String, Object> producer = new KafkaProducer<>(props)) {
            int counter = 0;
            // boolean exit = false;
            while (true) {
                // Send the record
                try {
                    Object sales;

                    sales = SalesDataGen.getSale(counter);

                    if (counter == 5) {
                        counter = 0;
                    }
                    System.out.println("------------------------- ");

                    // Create a sales record
                    ProducerRecord record = new ProducerRecord<>(topic, sales);
                    producer.send(record, new Callback() {
                        public void onCompletion(RecordMetadata metadata, Exception e) {
                            if(e != null) {
                                e.printStackTrace();
                            } else {
                                System.out.println("The offset of the order record we just sent is: " + metadata.offset());
                            }
                        }
                    }).get();
                    System.out.println(sales);
                    
                    // 10% of the time generate a duplicate
                    if (random.nextInt(10) == 0) {     
                        producer.send(record, new Callback() {
                            public void onCompletion(RecordMetadata metadata, Exception e) {
                                if(e != null) {
                                    e.printStackTrace();
                                } else {
                                    System.out.println("The offset of the order record we just sent is: " + metadata.offset());
                                }
                            }
                        }).get(); 
                        System.out.println("Duplicate sale event produced " + sales);
                    }       
                    
                    counter++;
                    
                    Thread.sleep(1000);
                    } catch (Exception e) {
                        // Catch and log the serialization error but continue to next record
                        // logger.error("Serialization error in ProducerApp.run: ", e);
                        e.printStackTrace();
                        continue;
                    }
            }
            } catch(Exception e){
                logger.error("Error in ProducerApp.run: ", e);
            }
            
        }


        public static void main ( final String[] args) throws Exception {
            if (args.length < 2) {
                logger.error(
                        "Provide the propertiesFile clientId  as arguments");
                System.exit(1);
            }
            ExecutorService exec = Executors.newFixedThreadPool(Integer.parseInt(args[1]));
            for(int i = 0; i < Integer.parseInt(args[1]); i++) {
                exec.submit(new Runnable() {
                    public void run() {
                        ProducerApp producer = new ProducerApp(args[0], "Pos_Store_"+(new Faker().address().cityName()));
                        System.out.println("Starting new Thread ");
                        producer.run();

                    }
                });
            }

            exec.shutdown();
            exec.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
            System.out.println("End of threads ==============================");

        }
    }
