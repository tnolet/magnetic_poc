'use strict';


// Declare app level module which depends on filters, and services
var app = angular.module('app', [
        'ngAnimate',
        'ngCookies',
        'ngStorage',
        'ui.router',
        'ui.bootstrap',
        'ui.load',
        'ui.jq',
        'ui.validate',
        'oc.lazyLoad',
        'pascalprecht.translate',
        'app.controllers',
        'app.services',
        'app.directives',
        'app.filters'
]);

app.config(['$stateProvider','$urlRouterProvider',
    function($stateProvider, $urlRouterProvider) {

        // when unmatched
        $urlRouterProvider.otherwise("/app/environments/list");

        // states
        $stateProvider
            .state('app', {
                abstract: true,
                url: '/app',
                templateUrl: 'demo/tpl/app.html',
                resolve: {
                    deps: ['uiLoad',
                        function( uiLoad ){
                            return uiLoad.load( [
                                'js/libs/moment.min.js'] );
                        }]
                }
            })
            //images
            .state('app.images', {
                abstract: true,
                url: '/images',
                templateUrl: 'demo/tpl/images.html'
            })
            .state('app.images.list', {
                url: '/list',
                templateUrl: 'demo/tpl/images.list.html'
            })
            .state('app.images.detail', {
                url: '/{imageId:[0-9]}',
                templateUrl: 'demo/tpl/images.detail.html'
            })
            // environments
            .state('app.environments', {
                abstract: true,
                url: '/environments',
                templateUrl: 'demo/tpl/environments.html'
            })
            .state('app.environments.list', {
                url: '/list',
                templateUrl: 'demo/tpl/environments.list.html'
            })
            .state('app.environments.detail', {
                url: '/{environmentId:[0-9]}',
                templateUrl: 'demo/tpl/environments.detail.html'
            })
            // services
            .state('app.services', {
                abstract: true,
                url: '/services',
                templateUrl: 'demo/tpl/services.html'
            })
            .state('app.services.list', {
                url: '/list',
                templateUrl: 'demo/tpl/services.list.html'
            })
            .state('app.services.detail', {
                url: '/{serviceId:[0-9]}',
                templateUrl: 'demo/tpl/services.detail.html'
            })
            .state('app.containers', {
                url: '/containers',
                templateUrl: 'demo/tpl/containers.html'
            })
            .state('app.containers.list', {
                url: '/containers/list',
                templateUrl: 'demo/tpl/containers.list.html'
            })
    }])

.constant('JQ_CONFIG', {
        easyPieChart:   ['js/jquery/charts/easypiechart/jquery.easy-pie-chart.js'],
        sparkline:      ['js/jquery/charts/sparkline/jquery.sparkline.min.js'],
        plot:           ['js/jquery/charts/flot/jquery.flot.min.js',
            'js/jquery/charts/flot/jquery.flot.resize.js',
            'js/jquery/charts/flot/jquery.flot.tooltip.min.js',
            'js/jquery/charts/flot/jquery.flot.spline.js',
            'js/jquery/charts/flot/jquery.flot.orderBars.js',
            'js/jquery/charts/flot/jquery.flot.pie.min.js'],
        slimScroll:     ['js/jquery/slimscroll/jquery.slimscroll.min.js'],
        sortable:       ['js/jquery/sortable/jquery.sortable.js'],
        nestable:       ['js/jquery/nestable/jquery.nestable.js',
            'js/jquery/nestable/nestable.css'],
        filestyle:      ['js/jquery/file/bootstrap-filestyle.min.js'],
        slider:         ['js/jquery/slider/bootstrap-slider.js',
            'js/jquery/slider/slider.css'],
        chosen:         ['js/jquery/chosen/chosen.jquery.min.js',
            'js/jquery/chosen/chosen.css'],
        TouchSpin:      ['js/jquery/spinner/jquery.bootstrap-touchspin.min.js',
            'js/jquery/spinner/jquery.bootstrap-touchspin.css'],
        wysiwyg:        ['js/jquery/wysiwyg/bootstrap-wysiwyg.js',
            'js/jquery/wysiwyg/jquery.hotkeys.js'],
        dataTable:      ['js/jquery/datatables/jquery.dataTables.min.js',
            'js/jquery/datatables/dataTables.bootstrap.js',
            'js/jquery/datatables/dataTables.bootstrap.css'],
        vectorMap:      ['js/jquery/jvectormap/jquery-jvectormap.min.js',
            'js/jquery/jvectormap/jquery-jvectormap-world-mill-en.js',
            'js/jquery/jvectormap/jquery-jvectormap-us-aea-en.js',
            'js/jquery/jvectormap/jquery-jvectormap.css'],
        footable:       ['js/jquery/footable/footable.all.min.js',
            'js/jquery/footable/footable.core.css']
    }
);