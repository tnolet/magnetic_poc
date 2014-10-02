'use strict';

/* Services */

angular.module('app.services', [])

    /* Feeds */

/**
 * Simple service that receives a SSE stream of load balancer metrics
 * @return register return a function to which you can register a callback.
 * Typically, you would register a callback from a controller to receive live updates
 * from the metrics feed in a controller $scope
 *
 */

.factory('loadBalancerMetricsFeed', function () {

        console.log("Registered service loadBalancerMetricsFeed")

        var source = new EventSource('/feeds/metrics/lb');

        var registerCallback = function(callback){
            source.addEventListener('message',function(msg){
            callback(JSON.parse(msg.data))
            })
        };

        return {
            register: function(callback) {
                registerCallback(callback)
            }
        };

});
