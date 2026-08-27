import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  {
    path: 'dashboard',
    loadComponent: () => import('./pages/dashboard/dashboard').then((m) => m.Dashboard),
  },
  {
    path: 'executions',
    loadComponent: () => import('./pages/executions/executions').then((m) => m.Executions),
  },
  {
    path: 'lessons',
    loadComponent: () => import('./pages/lessons/lessons').then((m) => m.Lessons),
  },
  {
    path: 'repertoire',
    loadComponent: () => import('./pages/repertoire/repertoire').then((m) => m.Repertoire),
  },
  {
    path: 'goals',
    loadComponent: () => import('./pages/goals/goals').then((m) => m.Goals),
  },
  {
    path: 'goals/:id',
    loadComponent: () => import('./pages/goals/goal-detail').then((m) => m.GoalDetailPage),
  },
  {
    // Stub por enquanto (Fase 3d) - o Modo Sessao de verdade (count-in, timer, metronomo
    // embutido, revisao) e a Fase 3e. Ver `pages/session/session.ts`.
    path: 'session',
    loadComponent: () => import('./pages/session/session').then((m) => m.SessionPage),
  },
  { path: '**', redirectTo: 'dashboard' },
];
