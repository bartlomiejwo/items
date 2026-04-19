from django.urls import path, include
from rest_framework.routers import DefaultRouter
from . import views


router = DefaultRouter()
router.register(r'actions', views.ScanActionActiveViewSet)

urlpatterns = [
    path('item_scan/create/', views.ItemScanCreateView.as_view(), name='item_scan_create'),
    path('item_scans/csv/', views.ItemScanCSVExportView.as_view(), name='item_scan_export_csv'),
    path('item_scans/csv/<int:year>/<int:month>/', views.FilteredItemScanCSVExportView.as_view(),
        name='filtered_item_scan_export_csv'),
    path('', include(router.urls))
]
