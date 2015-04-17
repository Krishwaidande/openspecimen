
angular.module('os.administrative.user', 
  [
    'os.administrative.user.dropdown',
    'os.administrative.user.list',
    'os.administrative.user.addedit',
    'os.administrative.user.detail',
    'os.administrative.user.roles'
  ])

  .config(function($stateProvider) {
    $stateProvider
      .state('user-list', {
        url: '/users',
        templateUrl: 'modules/administrative/user/list.html',
        controller: 'UserListCtrl',
        parent: 'signed-in'
      })
      .state('user-addedit', {
        url: '/user-addedit/:userId',
        templateUrl: 'modules/administrative/user/addedit.html',
        resolve: {
          user: function($stateParams, User) {
            if ($stateParams.userId) { 
              return User.getById($stateParams.userId);
            }
            return new User();
          }
        },
        controller: 'UserAddEditCtrl',
        parent: 'signed-in'
      })
      .state('user-detail', {
        url: '/users/:userId',
        templateUrl: 'modules/administrative/user/detail.html',
        resolve: {
          user: function($stateParams, User) {
            return User.getById($stateParams.userId);
          }
        },
        controller: 'UserDetailCtrl',
        parent: 'signed-in'
      })
      .state('user-detail.overview', {
        url: '/overview',
        templateUrl: 'modules/administrative/user/overview.html',
        parent: 'user-detail'
      })
      .state('user-detail.roles', {
        url: '/roles',
        templateUrl: 'modules/administrative/user/roles.html',
        resolve: {
          userRoles: function(user) {
            return user.getRoles();
          }
        },
        controller: 'UserRolesCtrl',
        parent: 'user-detail'
      })
  });
