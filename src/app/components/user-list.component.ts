import { Component, OnInit, signal, computed } from '@angular/core';
import { CdkDragDrop, CdkDrag, CdkDropList, CdkDragPlaceholder, CdkDragHandle, moveItemInArray } from '@angular/cdk/drag-drop';
import { UserService } from '../services/user.service';
import { User } from '../models/user.model';

@Component({
  selector: 'app-user-list',
  standalone: true,
  templateUrl: './user-list.component.html',
  styleUrl: './user-list.component.css',
  imports: [CdkDropList, CdkDrag, CdkDragPlaceholder, CdkDragHandle]
})
export class UserListComponent implements OnInit {
  users = signal<User[]>([]);
  loading = signal(true);
  saving = signal(false);
  error = signal<string | null>(null);

  // disable interactions while loading or saving
  isDisabled = computed(() => this.loading() || this.saving());

  constructor(private userService: UserService) {}

  ngOnInit(): void {
    this.loadUsers();
  }

  loadUsers(): void {
    this.loading.set(true);
    this.error.set(null);
    this.userService.getAllUsers().subscribe({
      next: (users) => {
        // sort by position to maintain correct order after refresh
        const sortedUsers = users.sort((a, b) => (a.position ?? 0) - (b.position ?? 0));
        this.users.set(sortedUsers);
        this.loading.set(false);
      },
      error: (err) => {
        console.error('Error loading users:', err);
        this.error.set('Failed to load users');
        this.loading.set(false);
      }
    });
  }

  drop(event: CdkDragDrop<User[]>): void {
    if (this.isDisabled() || event.previousIndex === event.currentIndex)
      return;

    // for rollback
    const previousUsers = this.users();

    // Optimistic update
    const currentUsers = [...previousUsers];
    moveItemInArray(currentUsers, event.previousIndex, event.currentIndex);
    this.users.set(currentUsers);

    // Save to database
    const userIds = currentUsers.map(user => user.id!);
    this.saving.set(true);
    this.error.set(null);

    this.userService.reorderUsers(userIds).subscribe({
      next: () => {
        this.saving.set(false);
      },
      error: (err) => {
        console.error('Error saving order:', err);
        this.error.set('Failed to save order');
        this.saving.set(false);
        // Rollback to previous state
        this.users.set(previousUsers);
      }
    });
  }

  deleteUser(id: number): void {
    if (this.isDisabled())  // Prevent delete while saving
      return;
    if (confirm('Are you sure you want to remove this user?')) {
      const previousUsers = this.users();
      this.users.set(previousUsers.filter(user => user.id !== id));
      this.userService.deleteUser(id).subscribe({
        error: (err) => {
          console.error('Error deleting user:', err);
          this.error.set('Failed to delete user');
          this.users.set(previousUsers); // Rollback on error
        }
      });
    }
  }
}
