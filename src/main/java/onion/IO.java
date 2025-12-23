package onion;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;

public class IO {
    private static final BufferedReader STDIN = new BufferedReader(new InputStreamReader(System.in));

    public static void print(Object o) {
        System.out.print(o);
    }

    public static void println(Object o) {
        System.out.println(o);
    }

    public static String readLine() {
        try {
            return STDIN.readLine();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String readln() {
        return readLine();
    }

    public static String readln(String prompt) {
        return input(prompt);
    }

    public static String input(String prompt) {
        System.out.print(prompt);
        try {
            return STDIN.readLine();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String readAll() throws IOException{
        return new String(
            System.in.readAllBytes(), 
            System.getProperty("file.encoding")
        );
    }
}
