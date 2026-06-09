import com.example.anujsharma.shuffler.utilities.YouTubeInAppClient;
public class test {
    public static void main(String[] args) throws Exception {
        YouTubeInAppClient client = new YouTubeInAppClient();
        System.out.println(client.resolveBestAudioStreamUrl("kJQP7kiw5Fk")); // Despacito
    }
}
