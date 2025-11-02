package com.cresterida.gateway.handlers;

import com.cresterida.gateway.registry.ServiceRegistry;
import io.vertx.ext.web.Router;

public class ApisServiceHandler {

    private  ServiceRegistry serviceRegistry;

    public ApisServiceHandler(
            ServiceRegistry serviceRegistry
    ) {
        this.serviceRegistry = serviceRegistry;
    }



    public void registerRoutes(Router router) {


    }




}
