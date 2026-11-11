import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

export const sessionCookieInterceptor: HttpInterceptorFn = (request, next) => {
  const router = inject(Router);
  return next(request.clone({ withCredentials: true })).pipe(catchError(error => {
    if (error.status === 428 && error.error?.code === 'PASSWORD_CHANGE_REQUIRED') {
      sessionStorage.setItem('cambioPasswordPendiente', 'true');
      void router.navigateByUrl('/cambiar-password');
    }
    return throwError(() => error);
  }));
};
