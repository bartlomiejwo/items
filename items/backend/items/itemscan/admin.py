from django.contrib import admin
from .models import ItemScan, Device, ScanAction


class DeviceAdmin(admin.ModelAdmin):
    model = Device
    list_display = ('location', 'token', 'active')


class ScanActionAdmin(admin.ModelAdmin):
    model = ScanAction
    list_display = ('name', 'active')


class ItemScanAdmin(admin.ModelAdmin):
    model = ItemScan
    list_display = ('device', 'action', 'code', 'timestamp')
    search_fields = ('device__location', 'action__name', 'code',)
    list_filter = ('device__location', 'action__name', 'timestamp',)


admin.site.register(Device, DeviceAdmin)
admin.site.register(ScanAction, ScanActionAdmin)
admin.site.register(ItemScan, ItemScanAdmin)
