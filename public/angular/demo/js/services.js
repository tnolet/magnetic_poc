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

})

.service('Streamliner', ['$rootScope', '$http', function ($rootScope, $http) {
    /* public */
    var service = {
        processes: [],

        addJob: function (jobId) {
            console.info('addJob', jobId);
            service.processes['job'+jobId] = {type: 'job', id: jobId};
            startPoller('job'+jobId);
        },

        removeJob: function (jobId) {
            // @todo: implement
            console.info('removeJob', jobId);
        }
    };

    /* private */
    var requestJobStatus = function(obj) {
        console.info('>> requestJobStatus');
        console.info(arguments);
        $http.get('/jobs/'+obj.id)
            .success(function(data, status, headers, config) {
                console.log(data);
                $rootScope.$broadcast('jobs.update', data);
                requestJobEvents(obj);
                // startPoller('job'+obj.id);
            })
            .error(function(data, status, headers, config) {
                console.error(data);
                startPoller('job'+obj.id);
            })
        ;
    };

    var requestJobEvents = function(obj) {
        console.info('>> requestJobEvents');
        console.info(arguments);
        $http.get('/jobs/'+obj.id+'/events')
            .success(function(data, status, headers, config) {
                console.log(data);
                $rootScope.$broadcast('jobevent.update', data);
                // requestJobEvents(obj);
                // @todo: if (status == 'LIVE' || status == 'DESTROYED')
                service.processes['job'+obj.id]['timerId'] = setTimeout(requestJobEvents.bind(service.processes, service.processes['job'+obj.id]), 5000);
            })
            .error(function(data, status, headers, config) {
                console.error(data);
                startPoller('job'+obj.id);
            })
        ;
    };

    var startPoller = function(identifier) {
        console.info('>> startPoller');
        console.log(service.processes);
        if (!service.processes[identifier]['timerId']) {
            service.processes[identifier]['timerId'] = -1;
        }
        // @todo: should be setInterval ?
        service.processes[identifier]['timerId'] = setTimeout(requestJobStatus.bind(service.processes, service.processes[identifier]), 0);
    };

    return service;
}]);
