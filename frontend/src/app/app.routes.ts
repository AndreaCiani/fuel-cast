import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    title: 'fuel-cast — trova carburante conveniente',
    loadComponent: () => import('./pages/explore/explore.component').then((m) => m.ExploreComponent),
  },
  { path: '**', redirectTo: '' },
];
