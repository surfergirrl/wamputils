package net.iqaros;

import java.util.List;

public class Main {
	public static void main(String... args) {
    	System.out.println("wamputil");
    	List.of(args).stream().forEach(System.out::println);
    }
}
