package onion;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;

public class IO {
    public static void print(Object o) {
        System.out.print(o);
    }

    public static void println(Object o) {
        System.out.println(o);
    }

    public static String readLine() {
        try(var scanner = new Scanner(System.in)) {
            return scanner.nextLine();
        }
    }

    public static String input(String prompt) {
        try(var scanner = new Scanner(System.in)) {
            System.out.print(prompt);
            return scanner.nextLine();
        }
    }

    public static String readAll() throws IOException{
        return new String(
            System.in.readAllBytes(), 
            System.getProperty("file.encoding")
        );
    }
}
