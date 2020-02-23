package me.srijan.vertx_circuit_breaker_example;

import io.reactivex.Flowable;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.reactivex.circuitbreaker.CircuitBreaker;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.Promise;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.http.HttpServerResponse;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.client.HttpResponse;
import io.vertx.reactivex.ext.web.client.WebClient;

public class Client extends AbstractVerticle {

  WebClient client;
  CircuitBreaker circuitBreaker;

  @Override
  public void start(io.vertx.core.Promise<Void> startPromise) throws Exception {

    WebClientOptions webClientOptions = new WebClientOptions();
    client = WebClient.create(vertx, webClientOptions);

    CircuitBreakerOptions options = new CircuitBreakerOptions()
            .setFallbackOnFailure(true) // do we call the fallback on failure
            .setMaxFailures(3) // number of failure before opening the circuit
            .setResetTimeout(5000) // time spent in open state before attempting to re-try
            .setTimeout(1000)  // consider a failure if the operation does not succeed in time
            .setMaxRetries(0); //how often the circuit breaker should try your code before failing

    circuitBreaker =
            CircuitBreaker.create("my-circuit-breaker", vertx, options)
                    .openHandler(v -> {
                      System.out.println("Circuit opened");
                    }).closeHandler(v -> {
              System.out.println("Circuit closed");
            });

    Router router = Router.router(vertx);
    router.route("/").handler(this::getSuperHeroesWithSuperPowers);

    Util.startHttpServer(vertx, router, 2222, startPromise);
  }

  private void getSuperHeroesWithSuperPowers(RoutingContext rc) {

      //write response in chunks
      HttpServerResponse serverResponse =
            rc.response().setChunked(true);

      client.get(1111, "localhost" ,"/superheroes")
            .rxSend()
            .map(HttpResponse::bodyAsJsonArray)
            .flatMapPublisher(Flowable::fromIterable)
            .flatMapSingle(hero -> {
                            System.out.println("getting super power for " + hero);
                            return circuitBreaker.rxExecuteWithFallback(
                                    future -> getSuperPower(hero.toString(), future),
                                    err -> {
                                        System.out.println("sending fallback response for " + hero);
                                        return new JsonObject()
                                            .put("hero", hero)
                                            .put("superpower", "null");
                                    }
                            );
                        }
            )
            .subscribe(
                    json -> writeChunkResponse(serverResponse, json),
                    throwable -> {
                        System.out.println("failed with " + throwable.getMessage());
                        rc.fail(throwable);
                    },
                    () -> {
                        System.out.println("ended");
                        if(!serverResponse.ended())
                            serverResponse.end();
                    }
            );

  }

    private void getSuperPower(String hero, Promise future){
        client.get(1111, "localhost" ,"/superpower")
                .addQueryParam("hero", hero)
                .rxSend()
                .subscribe(
                        response -> {
                            if(response.statusCode() == 200) {
                                future.complete(
                                        new JsonObject()
                                                .put("hero", hero)
                                                .put("superpower", response.bodyAsJsonObject().getString(hero))
                                );
                            }
                            else {
                                future.fail("failed");
                            }
                        },
                        throwable -> {
                            System.out.println("get superpower request failed for "+ hero);
                            future.fail(throwable);
                        }
                );
    }

    public static void writeChunkResponse(HttpServerResponse response, JsonObject superHero) {
        if(!response.ended()) {
            System.out.println("The super hero " + superHero.getString("hero") + " has super power " + superHero.getString("superpower"));
            response.write(
                    "The super hero " + superHero.getString("hero") + " has super power " + superHero.getString("superpower") + "\n"
            );
        }
    }

  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx();
    vertx.deployVerticle(new Client());
  }
}
