package com.vertx.examples;

import io.vertx.core.Vertx;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.OpenOptions;
/**
 * @author ccontrer
 * This is a simple example of how to use a file stream reader with vertx stream system.
 */
public class Example1 {
	public static void main(String...args) {
		Vertx vertx = Vertx.vertx();
		OpenOptions opt = new OpenOptions().setRead(true);
		vertx.fileSystem().open("build.gradle", opt, ar ->{
			if(ar.succeeded()) {
				AsyncFile file = ar.result();
				file.handler(System.out::println)
					.exceptionHandler(Throwable::printStackTrace)
					.endHandler(done -> System.out.println("\n ---DONE"));	
			} else {
				ar.cause().printStackTrace();
			}
		});
	}

}
