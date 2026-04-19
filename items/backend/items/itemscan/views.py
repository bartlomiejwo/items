import csv

from django.http import HttpResponse
from django.utils import timezone
from django.utils.translation import gettext_lazy as _

from rest_framework import generics, viewsets
from rest_framework.permissions import AllowAny
from rest_framework import status
from rest_framework.response import Response

from .models import ItemScan, Device, ScanAction
from .serializers import (
    ItemScanCreateSerializer, ScanActionNameSerializer,
    ItemScanExportCSVSerializer
)
from .mixins import DeviceTokenAuthenticationMixin


class ScanActionActiveViewSet(DeviceTokenAuthenticationMixin, viewsets.ReadOnlyModelViewSet):
    queryset = ScanAction.objects.filter(active=True)
    serializer_class = ScanActionNameSerializer


class ItemScanCreateView(DeviceTokenAuthenticationMixin, generics.CreateAPIView):
    permission_classes = [AllowAny]
    queryset = ItemScan.objects.all()
    serializer_class = ItemScanCreateSerializer
    
    def create(self, request, *args, **kwargs):
        timestamp = request.data.get('timestamp', None)
        request_data = request.data
        request_data['device'] = {'token': request.headers.get('Items-Device-Auth')}

        serializer = self.get_serializer(data=request_data)
        serializer.is_valid(raise_exception=True)
        
        if timestamp is None:
            serializer.save()
        else:
            serializer.save(timestamp=timestamp)

        serializer_data = serializer.data
        serializer_data.pop('device')
        
        return Response(serializer_data, status=status.HTTP_201_CREATED)


class ItemScanCSVExportView(generics.ListAPIView):
    queryset = ItemScan.objects.all()
    serializer_class = ItemScanExportCSVSerializer

    def get(self, request, *args, **kwargs):
        queryset = self.get_queryset()
        serializer = self.get_serializer(queryset, many=True)
        
        response = HttpResponse(content_type='text/csv')
        response['Content-Disposition'] = 'attachment; filename="item_scans.csv"'

        writer = csv.writer(response, delimiter=';')

        writer.writerow([_('Location'), _('Code'), _('Timestamp'), _('Action')])

        for row in serializer.data:
            timestamp_str = timezone.localtime(
                timezone.datetime.strptime(row['timestamp'], "%Y-%m-%d %H:%M:%S%z")
            ).strftime('%Y-%m-%d %H:%M:%S')

            writer.writerow([
                row['device']['location'],
                row['code'],
                timestamp_str,
                row['action']['name']
            ])

        return response


class FilteredItemScanCSVExportView(ItemScanCSVExportView):
    def get_queryset(self):
        year = self.kwargs.get('year')
        month = self.kwargs.get('month')

        queryset = super().get_queryset()
        if year:
            queryset = queryset.filter(timestamp__year=year)

            if month:
                queryset = queryset.filter(timestamp__month=month)

        return queryset
