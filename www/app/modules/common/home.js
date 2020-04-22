angular.module('openspecimen')
  .factory('HomePageSvc', function($q, $translate) {
    var pageCards = [];

    var sorted = false;

    function registerCards(cards) {
      angular.forEach(cards, registerCard);
    }

    function registerCard(card) {
      pageCards.push(card);
      sorted = false;
    }

    function loadCards() {
      if (sorted || pageCards.length == 0) {
        var q = $q.defer();
        q.resolve(pageCards);
        return q.promise;
      }

      return $translate(pageCards[0].title).then(
        function() {
          angular.forEach(pageCards,
            function(card) {
              card.title       = $translate.instant(card.title);
              card.description = $translate.instant(card.description);
            }
          );

          pageCards.sort(function(c1, c2) { return c1.title.localeCompare(c2.title); });
          return pageCards;
        }
      );
    }

    function renderElement(opts, rootTag, idx, type) {
      var rootEl = angular.element(rootTag || '<div/>');
      if (type == 'card') {
        rootEl.addClass('os-home-item');
      }

      if (opts.ctrl) {
        rootEl.attr('ng-controller', opts.ctrl);
      }

      if (opts.showIf == 'admin') {
        rootEl.attr('show-if-admin', '');
      } else if (opts.showIf == 'institute-admin') {
        rootEl.attr('show-if-admin', 'institute');
      } else if (opts.showIf) {
        var ifOpts = 'hpOpts_' + idx;
        rootEl.attr('ng-init', ifOpts + '=' + JSON.stringify(opts.showIf));
        rootEl.attr('show-if-allowed', ifOpts);
      }

      angular.forEach(opts.attrs,
        function(value, key) {
          rootEl.attr(key, value);
        }
      );
        
      var link = angular.element('<a/>');
      if (opts.sref) {
        link.attr('ui-sref', opts.sref);
      } else if (opts.href) {
        link.attr('href', opts.href);
      }

      if (opts.newTab) {
        link.attr('target', '_blank');
      }

      if (type == 'card') {
        var icon = angular.element('<span class="os-home-item-icon"/>')
          .append(angular.element('<span/>').addClass(opts.icon));
        link.append(icon);

        var info = angular.element('<span class="os-home-item-info"/>')
          .append(angular.element('<h3/>').append(opts.title))
          .append(angular.element('<span/>').append(opts.description));
        link.append(info);
      } else {
        link.append(angular.element('<span/>').append(opts.title));
      }

      rootEl.append(link);
      return rootEl;
    }

    function renderElements(containerEl, type) {
      return loadCards().then(
        function(cards) {
          angular.forEach(cards,
            function(card, idx) {
              var cardEl = renderElement(card, '<li/>', idx, type);
              containerEl.append(cardEl);
            }
          );

          return containerEl;
        }
      );
    }

    return {
      registerCard: registerCard,

      registerCards: registerCards,

      renderElements: renderElements
    }
  })

  .directive('osHomePageCards', function($compile, HomePageSvc) {
    return {
      restrict: 'E',

      replace: true,

      template:
        '<ul class="os-home-items-container">' +
        '</ul>',

      link: function(scope, element, attrs) { 
        HomePageSvc.renderElements(element, 'card').then(
          function() {
            $compile(element.contents())(scope);
          }
        );
      }
    }
  })

  .directive('osAppMenuItems', function($compile, HomePageSvc) {
    return {
      restrict: 'E',

      replace: true,

      template:
        '<ul role="menu">' +
        '  <li>' +
        '    <a ui-sref="home" translate="menu.home">Home</a>' +
        '  </li>' +
        '</ul>',

      link: function(scope, element, attrs) {
        HomePageSvc.renderElements(element).then(
          function() {
            $compile(element.contents())(scope);
          }
        );
      }
    }
  })

  .run(function(HomePageSvc) {
    HomePageSvc.registerCards(
      [
        {
          showIf: {resource: 'CollectionProtocol', operations: ['Read']},
          sref: 'cp-list',
          icon: 'fa fa-calendar',
          title: 'menu.collection_protocols',
          description: 'menu.cp_desc'
        },

        {
          showIf: 'admin',
          sref: 'institute-list',
          icon: 'fa fa-institution',
          title: 'menu.institutes',
          description: 'menu.institutes_desc'
        },

        {
          showIf: {resource: 'User', operations: ['Create', 'Update']},
          sref: 'user-list',
          icon: 'fa fa-user',
          title: 'menu.users',
          description: 'menu.users_desc'
        },

        {
          showIf: {resource: 'User', operations: ['Create', 'Update']},
          sref: 'role-list',
          icon: 'fa fa-lock',
          title: 'menu.roles',
          description: 'menu.roles_desc'
        },

        {
          showIf: 'institute-admin',
          sref: 'site-list',
          icon: 'fa fa-hospital-o',
          title: 'menu.sites',
          description: 'menu.sites_desc'
        },

        {
          showIf: {resource: 'StorageContainer', operations: ['Read']},
          sref: 'container-list',
          icon: 'fa fa-dropbox',
          title: 'menu.containers',
          description: 'menu.containers_desc'
        },

        {
          showIf: {resource: 'Query', operations: ['Read']},
          sref: 'query-list',
          icon: 'fa fa-dashboard',
          title: 'menu.queries',
          description: 'menu.queries_desc'
        },

        {
          showIf: {resources: ['Specimen', 'PrimarySpecimen'], operations: ['Read']},
          sref: 'specimen-lists',
          icon: 'fa fa-shopping-cart',
          title: 'menu.specimen_lists',
          description: 'menu.specimen_lists_desc'
        },

        {
          sref: 'form-list',
          icon: 'fa fa-copy',
          title: 'menu.forms',
          description: 'menu.forms_desc',
          attrs: {
            'ng-if': 'currentUser.admin || currentUser.instituteAdmin || currentUser.manageForms'
          }
        },

        {
          showIf: {resource: 'DistributionProtocol', operations: ['Read']},
          sref: 'dp-list',
          icon: 'fa fa-truck',
          title: 'menu.distribution_protocols',
          description: 'menu.dp_desc'
        },
          
        {
          showIf: {resource: 'Order', operations: ['Read']},
          sref: 'order-list',
          icon: 'fa fa-share',
          title: 'menu.distribution_orders',
          description: 'menu.distribution_orders_desc'
        },

        {
          showIf: {resource: 'ShippingAndTracking', operations: ['Read']},
          sref: 'shipment-list',
          icon: 'fa fa-paper-plane-o',
          title: 'menu.shipping_and_tracking',
          description: 'menu.shipping_and_tracking_desc'
        },

        {
          showIf: {resource: 'ScheduledJob', operations: ['Read']},
          sref: 'job-list',
          icon: 'fa fa-gears',
          title: 'menu.jobs',
          description: 'menu.jobs_desc'
        },

        {
          showIf: 'institute-admin',
          sref: 'consent-statement-list',
          icon: 'fa fa-file-text-o',
          title: 'menu.consent_statements',
          description: 'menu.consent_statements_desc'
        },

        {
          href: '{{userCtx.trainingUrl}}',
          icon: 'fa fa-graduation-cap',
          title: 'menu.training',
          description: 'menu.training_desc',
          newTab: true
        },

        {
          showIf: 'admin',
          sref: 'settings-list',
          icon: 'fa fa-wrench',
          title: 'menu.settings',
          description: 'menu.settings_desc'
        }
      ]
    );
  });
