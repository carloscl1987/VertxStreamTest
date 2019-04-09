package com.vertx.examples;

import java.io.File;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

import ch.qos.logback.classic.Logger;
import io.netty.handler.codec.http.HttpRequest;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class Jukebox extends AbstractVerticle{
	private enum State {PLAYING, PAUSED}
	private State currentMode = State.PAUSED;
	private final Queue<String> playlist = new ArrayDeque<>();
	private final Set streamers = new HashSet<>();

	@Override
	public void start() throws Exception {
		EventBus eventbus = vertx.eventBus();
		eventbus.consumer("jukebox.list", this::list);
		eventbus.consumer("jukebox.schedule", this::schedule);
		eventbus.consumer("jukebox.play", this::play);
		eventbus.consumer("jukebox.pause",this::pause);
		
		vertx.createHttpServer()
			.requestHandler(this::httpHandler)
			.listen(8080);
	}
	
	private void play(Message<?> request) {
		currentMode = State.PLAYING;
	}
	
	private void pause(Message<?> request) {
		currentMode = State.PAUSED;
	}
	
	private void list(Message<?> request) {
		vertx.fileSystem().readDir("tracks", ".*mp3$", ar ->{
			if (ar.succeeded()) {
				List<String> files = ar.result()
										.stream()
										.map(File::new)
										.map(File::getName)
										.collect(Collectors.toList());
				JsonObject json = new JsonObject().put("files", new JsonArray(files));
				
				request.reply(json);
				
			} else {
				System.out.println(ar.cause());
				request.fail(500, ar.cause().getMessage());
			}
		});
	}
	
	private void schedule(Message<JsonObject> request) {
		String file = request.body().getString("file");
		if (playlist.isEmpty() && currentMode == State.PAUSED) {
			currentMode = State.PLAYING;
		}
		playlist.offer(file);
	}
	
	private void download(String path, HttpServerRequest request) {
		String file = "tracks/" + path;
		
		if(!vertx.fileSystem().existsBlocking(file)) {
			request.response().setStatusCode(404).end();
			return;
		}
		
		OpenOptions opts = new OpenOptions().setRead(true);
		vertx.fileSystem().open(file,  opts,  ar ->{
			if (ar.succeeded()) {
				downloadFile(ar.result(), request);
			} else {
				System.out.println("Read failed " + ar.cause());
				request.response().setStatusCode(500).end();
			}
		});
	}
	
	private void downloadFile(AsyncFile file, HttpServerRequest request) {
		HttpServerResponse response = request.response();
		
		response.setStatusCode(200)
				.putHeader("Content-Type", "audio/mpeg")
				.setChunked(true);
		
		file.handler(buffer -> {
			response.write(buffer);
			if (response.writeQueueFull()) {
				file.pause();
				response.drainHandler(v -> {
					file.resume();
				});
			}
			file.endHandler(v -> response.end());
		});
	}
	
	private void httpHandler(HttpServerRequest request) {
		if ( "/".equals(request.path())) {
			openAudioStream(request);
			return;
		}
		
		if (request.path().startsWith("/download/")) {
			String sanitizedPath = request.path().substring(10).replaceAll("/", "");
			download(sanitizedPath, request);
			return;
		}
		
		request.response().setStatusCode(404).end();
	}
	
	private void openAudioStream(HttpServerRequest request) {
		HttpServerResponse response = request.response().putHeader("Content-Type", "audio/mpeg").setChunked(true);
		
		streamers.add(response);
		response.endHandler(v -> {
			streamers.remove(response);
			System.out.println("A streamer left");
		});
	}
}
