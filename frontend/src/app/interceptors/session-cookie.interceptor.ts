import { HttpInterceptorFn } from '@angular/common/http';

export const sessionCookieInterceptor: HttpInterceptorFn = (request, next) =>
  next(request.clone({ withCredentials: true }));
