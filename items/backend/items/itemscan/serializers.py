from rest_framework import serializers
from django.utils.translation import gettext_lazy as _

from .models import ItemScan, Device, ScanAction


class ScanActionNameSerializer(serializers.ModelSerializer):
    class Meta:
        model = ScanAction
        fields = ['name',]


class DeviceTokenSerializer(serializers.ModelSerializer):
    class Meta:
        model = Device
        fields = ['token',]


class DeviceLocationSerializer(serializers.ModelSerializer):
    class Meta:
        model = Device
        fields = ['location',]


class ItemScanCreateSerializer(serializers.ModelSerializer):
    device = DeviceTokenSerializer(read_only=True)
    action = ScanActionNameSerializer(read_only=True)
    timestamp = serializers.DateTimeField(format="%Y-%m-%d %H:%M:%S%z", required=False)

    class Meta:
        model = ItemScan
        fields = ['code', 'timestamp', 'device', 'action']

    def create(self, validated_data):
        device_data = self.initial_data.get('device')
        action_data = self.initial_data.get('action')

        device_token = device_data.get('token') if device_data else None
        action_name = action_data.get('name') if action_data else None

        if device_token is None:
            raise serializers.ValidationError(_('Device token is missing.'))

        try:
            device = Device.objects.get(token=device_token, active=True)
        except Device.DoesNotExist:
            raise serializers.ValidationError(_('Device not found.'))

        if action_name is None:
            raise serializers.ValidationError(_('Action name is missing.'))

        try:
            action = ScanAction.objects.get(name=action_name, active=True)
        except ScanAction.DoesNotExist:
            raise serializers.ValidationError(_('Action not found.'))

        item_scan = ItemScan.objects.create(device=device, action=action, **validated_data)
        return item_scan


class ItemScanExportCSVSerializer(serializers.ModelSerializer):
    device = DeviceLocationSerializer(read_only=True)
    action = ScanActionNameSerializer(read_only=True)
    timestamp = serializers.DateTimeField(format="%Y-%m-%d %H:%M:%S%z", required=False)

    class Meta:
        model = ItemScan
        fields = ['code', 'timestamp', 'device', 'action']
