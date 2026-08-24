import java.net.HttpURLConnection;
import java.net.URI;

public final class HealthCheck {

    private HealthCheck() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.exit(2);
        }

        HttpURLConnection connection = (HttpURLConnection) URI.create(args[0]).toURL().openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(2_000);
        connection.setReadTimeout(2_000);

        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                System.exit(1);
            }
        } finally {
            connection.disconnect();
        }
    }
}
