package namecheap;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Corey S. Schooler
 */
public class Client {
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private NamecheapConfig config;
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private Client() {
        ObjectMapper mapper = new ObjectMapper();
        try {
            config = mapper.readValue(new File("namecheap.config"), NamecheapConfig.class);
        } catch (JsonParseException e) {
            logger.error("An error occurred parsing namecheap.config.", e);
        } catch (JsonMappingException e) {
            logger.error("An error occurred mapping namecheap.config.", e);
        } catch (IOException e) {
            logger.error("An IOException occurred while working with namecheap.config.", e);
        }

        if (config == null) {
            logger.info("Configuration was not found; switching to manual entry mode.");
            System.out.println("namecheap.config not found, switching to manual entry...");
            final String host = getField("Please enter the host (example: www, @, news, etc): ", "host");
            final String domain = getField("Please enter the domain (example: example.com): ", "domain");
            final String password = getField(
                    "Please enter the password (this was supplied wihtin the Dynamic DNS section on namecheap.com): ",
                    "password");
            final Integer refreshRate = getIntegerField(
                    "Please enter the number (in seconds) of how often to update namecheap.com: ", "refresh rate");
            config = new NamecheapConfig(host, domain, password, refreshRate);

        }

        if (config == null) {
            throw new IllegalStateException("failed to create config...");
        }
    }

    public static void main(final String[] args) {
        new Client().run();
    }

    private void run() {
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                HttpClient httpclient = new DefaultHttpClient();
                HttpGet getIpRequest = new HttpGet("http://automation.whatismyip.com/n09230945.asp");
                try {
                    HttpResponse response = httpclient.execute(getIpRequest);
                    HttpEntity entity = response.getEntity();
                    InputStream stream = entity.getContent();
                    String ip = null;
                    try {
                        ip = IOUtils.toString(stream);
                    } finally {
                        stream.close();
                    }

                    logger.info("Senging ip address: {}", ip);
                    HttpGet setIpRequest = new HttpGet(getSetIpAddressUrl(ip));
                    HttpResponse setIpResponse = httpclient.execute(setIpRequest);
                    entity = setIpResponse.getEntity();
                    stream = entity.getContent();
                    try {
                        logger.info("Recieved response: {}",IOUtils.toString(stream));
                    } finally {
                        stream.close();
                    }
                } catch (ClientProtocolException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }, 0, config.getRefreshRate(), SECONDS);
    }

    private Integer getIntegerField(final String prompt, final String fieldName) {
        final String stringValue = getField(prompt, fieldName);
        int value = 0;
        try {
            value = Integer.parseInt(stringValue);
        } catch (NumberFormatException e) {
            logger.error("A non-numerica value was found for {}.", fieldName, e);
        }
        if (value <= 0) {
            System.out.println(fieldName + " must be larger than zero.");
            return getIntegerField(prompt, fieldName);
        }
        return value;
    }

    private String getField(final String prompt, final String fieldName) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        try {
            System.out.print(prompt);
            String value = reader.readLine();
            if (value == null || value.trim().isEmpty()) {
                System.out.println(fieldName + " is required.");
                getField(prompt, fieldName);
            }
            return value;
        } catch (IOException e) {
            logger.error("An error occurred loading {}.", fieldName, e);
            throw new IllegalStateException(e);
        }
    }

    private String getSetIpAddressUrl(final String ip) {
        StringBuilder builder = new StringBuilder();
        builder.append("https://dynamicdns.park-your-domain.com/update?host=");
        builder.append(config.getHost());
        builder.append("&domain=");
        builder.append(config.getDomain());
        builder.append("&password=");
        builder.append(config.getPassword());
        builder.append("&ip=");
        builder.append(ip);
        return builder.toString();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NamecheapConfig {
        private String host, domain, password;
        private Integer refreshRate;

        @JsonCreator
        public NamecheapConfig(@JsonProperty("host") final String host, @JsonProperty("domain") final String domain,
                @JsonProperty("password") final String password, @JsonProperty("refreshRate") final Integer refreshRate) {
            if (host == null) {
                throw new IllegalArgumentException("host cannot be null");
            }
            if (domain == null) {
                throw new IllegalArgumentException("domain cannot be null");
            }
            if (password == null) {
                throw new IllegalArgumentException("password cannot be null");
            }
            if (refreshRate == null) {
                throw new IllegalArgumentException("refreshRate cannot be null");
            }
            this.host = host;
            this.domain = domain;
            this.password = password;
            this.refreshRate = refreshRate;
        }

        public void setHost(final String host) {
            this.host = host;
        }

        public String getHost() {
            return host;
        }

        public void setDomain(final String domain) {
            this.domain = domain;
        }

        public String getDomain() {
            return domain;
        }

        public void setPassword(final String password) {
            this.password = password;
        }

        public String getPassword() {
            return password;
        }

        public void setRefreshRate(final Integer refreshRate) {
            this.refreshRate = refreshRate;
        }

        public Integer getRefreshRate() {
            return refreshRate;
        }
    }
}