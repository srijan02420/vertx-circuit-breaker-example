package me.srijan.vertx_circuit_breaker_example;

import io.vertx.core.Promise;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.RxHelper;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.http.HttpServer;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Server extends AbstractVerticle {

	ArrayList<String> list = new ArrayList<>();

	Map<String, String> powers = new HashMap<>();

	@Override
	public void start(Promise<Void> startPromise) throws Exception {

		list.add("superman");
		list.add("batman");
		list.add("spiderman");

		powers.put(list.get(0), "flying");
		powers.put(list.get(1), "deep-voice");
		powers.put(list.get(2), "spider-web");

		Router router = Router.router(this.vertx);

		router.get("/superheroes").handler(this::getSuperHeroes);
		router.get("/superpower").handler(this::getSuperPower);
//		router.get("/superpower").handler(req -> req.response().setStatusCode(503).end());

		HttpServer httpServer = vertx.createHttpServer();

		httpServer.requestStream()
				.toFlowable()
				// Pause receiving buffers
				.map(HttpServerRequest::pause)
				.onBackpressureDrop(req -> req.response().setStatusCode(503).end())
				//Create a scheduler for a Vertx object, actions are executed on the event loop.
				.observeOn(RxHelper.scheduler(vertx.getDelegate()))
				.subscribe(req -> {
					// Resume receiving buffers again
					req.resume();
					router.handle(req);
				});

		httpServer.rxListen(1111)
				.subscribe(res -> {
					System.out.println("started http server");
					startPromise.complete();
				}, error -> {
					System.out.println("failed to start http server with " + error.getMessage());
					startPromise.fail(error);
				})
		;
	}

	private void getSuperHeroes(RoutingContext routingContext){
		routingContext.response().end(Json.encodePrettily(list));
	}

	private void getSuperPower(RoutingContext routingContext){
		String hero = routingContext.request().getParam("hero");
		if(hero == null || hero.isEmpty())
			routingContext.response().setStatusCode(400).end();
		else
			routingContext.response().end(
					Json.encodePrettily(
							new JsonObject().put(hero,
								powers.getOrDefault(
										routingContext.request().getParam("hero"),
										null
								)
							)
					)
			);
	}

	public static void main(String[] args) {
		Vertx vertx = Vertx.vertx();
		vertx.deployVerticle(new Server());
	}

}
