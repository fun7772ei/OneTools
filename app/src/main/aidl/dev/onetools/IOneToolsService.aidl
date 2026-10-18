package dev.onetools;
import android.os.Bundle;
interface IOneToolsService {
 Bundle battery() = 0;
 Bundle shutter() = 1;
 Bundle setShutter(int value) = 2;
 int uid() = 3;
 Bundle batteryProperty(int propertyId) = 4;
 Bundle dischargeStats() = 5;
 Bundle batteryHalInfo() = 6;
 void destroy() = 16777114;
}
