package me.srijan.vertx_circuit_breaker_example;

import io.reactivex.Flowable;
import io.reactivex.Single;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.reactivex.RxHelper;
import io.vertx.reactivex.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.reactivex.circuitbreaker.CircuitBreaker;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.http.HttpServer;
import io.vertx.reactivex.core.http.HttpServerRequest;
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
            .setFallbackOnFailure(true)
            .setMaxFailures(3)
            .setResetTimeout(5000)
            .setTimeout(1000);

    circuitBreaker =
            CircuitBreaker.create("my-circuit-breaker", vertx, options)
                    .openHandler(v -> {
                      System.out.println("Circuit opened");
                    }).closeHandler(v -> {
              System.out.println("Circuit closed");
            });

    Router router = Router.router(vertx);
    router.route("/").handler(this::getSuperHeroesWithSuperPowers);

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

    httpServer.rxListen(2222)
            .subscribe(res -> {
              System.out.println("started client http server");
              startPromise.complete();
            }, error -> {
              System.out.println("failed to start client http server with " + error.getMessage());
              startPromise.fail(error);
            })
    ;
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
                        else{
                            future.fail("failed");
                        }
                    },
                    throwable -> future.fail(throwable)
            );
  }

  private void getSuperHeroesWithSuperPowers(RoutingContext rc) {
    HttpServerResponse serverResponse =
            rc.response().setChunked(true);

    Single<JsonArray> single = client.get(1111, "localhost" ,"/superheroes")
            .rxSend()
            .map(HttpResponse::bodyAsJsonArray);

    single.flatMapPublisher(
            Flowable::fromIterable
    )
            .flatMapSingle(hero ->
                    circuitBreaker.rxExecuteWithFallback(
                            future -> getSuperPower(hero.toString(), future),
                            err -> new JsonObject()
                                    .put("hero", hero)
                                    .put("superpower", "null")
                    ))
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

    public static void writeChunkResponse(HttpServerResponse response, JsonObject superHero) {
        System.out.println("The super hero " + superHero.getString("hero") + " has super power " + superHero.getString("superpower"));
        response.write(
                "The super hero " + superHero.getString("hero") + " has super power " + superHero.getString("superpower") + "\n"
        );
    }

  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx();
    vertx.deployVerticle(new Client());
  }
}
