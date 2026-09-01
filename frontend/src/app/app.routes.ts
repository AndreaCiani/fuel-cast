import { Routes } from '@angular/router';

import { managerGuard } from './guards/manager.guard';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    title: 'fuel-cast — trova carburante conveniente',
    loadComponent: () => import('./pages/explore/explore.component').then((m) => m.ExploreComponent),
  },
  {
    path: 'manager/login',
    title: 'Area gestori · fuel-cast',
    loadComponent: () =>
      import('./pages/manager-login/manager-login.component').then((m) => m.ManagerLoginComponent),
  },
  {
    path: 'manager',
    canActivate: [managerGuard],
    title: 'Dashboard gestore · fuel-cast',
    loadComponent: () =>
      import('./pages/manager-dashboard/manager-dashboard.component').then((m) => m.ManagerDashboardComponent),
  },
  { path: '**', redirectTo: '' },
];
