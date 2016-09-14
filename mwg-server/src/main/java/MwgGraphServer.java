import org.mwg.Callback;
import org.mwg.Graph;
import org.mwg.GraphBuilder;
import org.mwg.WSServer;

/**
 * Created by ludovicmouline on 14/09/16.
 */
public class MwgGraphServer {

    public static final String DB_PATH = "mwgDB";

    public static void main(String[] args) {
        final Graph graph = new GraphBuilder()
                    .withStorage(new org.mwg.LevelDBStorage(DB_PATH))
                    .withMemorySize(1000)
                    .build();

        final WSServer server = new WSServer(graph,9876);

        graph.connect(new Callback<Boolean>() {
            @Override
            public void on(Boolean aBoolean) {
                server.start();
            }
        });

        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                server.stop();
                graph.disconnect(null);
            }
        }));
    }
}
