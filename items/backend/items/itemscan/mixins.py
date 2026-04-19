from django.http import JsonResponse
from .models import Device


class DeviceTokenAuthenticationMixin:
    def dispatch(self, request, *args, **kwargs):
        token = request.headers.get('Items-Device-Auth')

        if not self.validate_token(token):
            return JsonResponse({'error': 'Unauthorized'}, status=401)
        
        return super().dispatch(request, *args, **kwargs)
    
    def validate_token(self, token):
        if not token:
            return False

        return True if Device.objects.filter(token=token, active=True).exists() is not None else False
