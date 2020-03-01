package me.srijan.vertx_circuit_breaker_example;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.reactivex.RxHelper;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.http.HttpServer;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.Router;

public class Util {

	private static Logger LOG = LoggerFactory.getLogger(Util.class);

	public static void startHttpServer(Vertx vertx, Router router, int port, io.vertx.core.Promise<Void> startPromise){
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

		httpServer.rxListen(port)
				.subscribe(res -> {
					LOG.info("started http server");
					startPromise.complete();
				}, error -> {
					LOG.error("failed to start client http server with " + error.getMessage());
					startPromise.fail(error);
				})
		;
	}

}
