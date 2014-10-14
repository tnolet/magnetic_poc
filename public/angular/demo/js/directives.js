'use strict';

    /* directives */

angular.module('app.directives', [])

    /* graphs */

    .directive('sparklinesChart', function () {
        return {
            scope: {
                metrics: '=',
                start: '='
            },
            template: '<div class="spark-lines"></div>',
            restrict: 'E',
            link: function(scope, element, attrs) {

                var myvalues = [0];
                var prevVal = 0;

                scope.$watch('metrics', function(newVal) {

                    if (!newVal) {
                        newVal = prevVal
                    }
                    prevVal = newVal;

                    if (myvalues.length == attrs.history) {
                        myvalues.shift()

                    }
                    myvalues.push(parseInt(newVal));

                    console.log(myvalues)

                    var graph = element.find('.spark-lines');

                    if (attrs.type == 'bar') {
                        // calculate the bar width based on history  and containing element's width
                        var barSpacing = 1;
                        var barWidth = (graph.width() - ( attrs.history * barSpacing)) / attrs.history ;

                        if (graph.sparkline) {
                            graph.sparkline(myvalues,{type: 'bar', height: attrs.height, barWidth: barWidth, barSpacing: barSpacing, barColor:'#dce5ec'});
                        }
                    } else if ( attrs.type == 'line') {
                        if (graph.sparkline) {
                            graph.sparkline(myvalues, {type: 'line', height: attrs.height, width: '100%', lineWidth:2, valueSpots:{'0:':'#fff'}, lineColor:'#fff', spotColor:'#fff', fillColor:'#dce5ec', highlightLineColor:'#fff', spotRadius:3});
                        }
                    }
                });
            }
        };
    });