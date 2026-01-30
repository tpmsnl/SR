import { Routes } from '@angular/router';
import { UserListComponent } from './components/user-list.component';

export const routes: Routes = [
  { path: '', component: UserListComponent },
  { path: 'users', component: UserListComponent }
];
