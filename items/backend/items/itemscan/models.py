import uuid
from hashlib import sha256

from django.db import models
from django.utils.translation import gettext_lazy as _
from django.utils import timezone


class Device(models.Model):
    class Meta:
        verbose_name = _('device')
        verbose_name_plural = _('devices')

    location = models.CharField(max_length=100, unique=True, verbose_name=_('Location'))
    token = models.CharField(max_length=32, unique=True, blank=True, verbose_name=_('Token'))
    active = models.BooleanField(default=True, verbose_name=_('Active'))

    def __str__(self):
        return self.location
    
    def save(self, *args, **kwargs):
        if not self.token:
            self.token = uuid.uuid4().hex

        super().save(*args, **kwargs)


class ScanAction(models.Model):
    class Meta:
        verbose_name = _('scan action')
        verbose_name_plural = _('scan actions')

    name = models.CharField(max_length=50, unique=True, verbose_name=_('Name'))
    active = models.BooleanField(default=True, verbose_name=_('Active'))

    def __str__(self):
        return self.name


class ItemScan(models.Model):
    class Meta:
        verbose_name = _('item scan')
        verbose_name_plural = _('item scans')
        ordering = ['-timestamp']

    code = models.CharField(default='0', max_length=20, verbose_name=_('Code'))
    timestamp = models.DateTimeField(default=timezone.now, verbose_name=_('Timestamp'))
    device = models.ForeignKey(Device, on_delete=models.PROTECT, verbose_name=_('Device'))
    action = models.ForeignKey(ScanAction, on_delete=models.PROTECT, verbose_name=_('Action'))

    def __str__(self):
        return f'{self.code} - {self.timestamp}'
