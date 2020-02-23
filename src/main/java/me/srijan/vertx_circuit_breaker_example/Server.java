package me.srijan.vertx_circuit_breaker_example;

import io.vertx.core.Promise;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.Vertx;
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

		Util.startHttpServer(vertx, router, 1111, startPromise);
	}

	private void getSuperHeroes(RoutingContext routingContext){
		System.out.println("getting all superheroes");
		routingContext.response().end(Json.encodePrettily(list));
	}

	private void getSuperPower(RoutingContext routingContext){
		System.out.println("getting superpower for " + routingContext.request().getParam("hero"));
		if(routingContext.request().getParam("fail").equals("true"))
			routingContext.response().setStatusCode(503).end();
		try {
			Thread.sleep(200 + (int) (Math.random()*300));
			String hero = routingContext.request().getParam("hero");
			if(hero == null || hero.isEmpty())
				routingContext.response().setStatusCode(400).end();
			else {
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
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		Vertx vertx = Vertx.vertx();
		vertx.deployVerticle(new Server());
	}

}
