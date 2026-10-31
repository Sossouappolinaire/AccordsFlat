package bj.radio;

import java.io.File;
import java.lang.reflect.Method;

/**
 * Convertit un WAV stéréo 44,1 kHz en MP3 avec la bibliothèque LAME « Mp3Converter »
 * (com.github.Jay-Goo:Mp3Converter, voir build.gradle).
 * Appelée par réflexion pour que la compilation ne dépende pas du nom exact du paquet de la bibliothèque.
 */
public class Mp3Encoder {

    private static final String[] CANDIDATES = {
            "com.jaygoo.library.converter.Mp3Converter",
            "com.jaygoo.mp3converter.Mp3Converter",
            "com.jay.mp3converter.Mp3Converter",
    };

    private static Class<?> find() throws ClassNotFoundException {
        for (String name : CANDIDATES) {
            try { return Class.forName(name); } catch (ClassNotFoundException ignored) { }
        }
        throw new ClassNotFoundException("Encodeur MP3 introuvable (bibliothèque Mp3Converter)");
    }

    /** Bloquant : à appeler dans un thread. */
    public static synchronized void wavToMp3(File wav, File mp3) throws Exception {
        Class<?> k = find();
        Method init = k.getMethod("init", int.class, int.class, int.class, int.class, int.class, int.class);
        Method convert = k.getMethod("convertMp3", String.class, String.class);
        if (mp3.exists()) mp3.delete();
        // entrée 44,1 kHz, 2 canaux, CBR, sortie 44,1 kHz, 192 kbit/s, qualité 5
        init.invoke(null, 44100, 2, 0, 44100, 192, 5);
        convert.invoke(null, wav.getPath(), mp3.getPath());
        if (!mp3.exists() || mp3.length() == 0) throw new Exception("La conversion MP3 a échoué");
    }
}
