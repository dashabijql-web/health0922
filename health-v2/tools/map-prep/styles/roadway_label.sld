<?xml version="1.0" encoding="UTF-8"?>
<!--
  巷道名称（docs/06 第三节、docs/09 第四节）：白底黑字的小标签，按 CAD 里的角度沿巷道摆放。
  - 字体：文泉驿微米黑，装在 GeoServer 镜像里（tools/dev/geoserver/Dockerfile），不装会显示成方块。
  - 白底：用白色光晕做出来（见下面 Halo 的注释）。
  - 角度：CAD 的 angle 是逆时针度数，SLD 的 Rotation 是顺时针，所以取负；
    90°–270° 之间的字会倒过来，再转 180° 让它正着读。
  - 缩小到比例尺 1:60000 以外不画文字（整个井田约 1:25000 就能看全）；互相压住的标签由 GeoServer 自动去掉。
-->
<StyledLayerDescriptor version="1.0.0"
    xmlns="http://www.opengis.net/sld" xmlns:ogc="http://www.opengis.net/ogc"
    xmlns:xlink="http://www.w3.org/1999/xlink" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.opengis.net/sld http://schemas.opengis.net/sld/1.0.0/StyledLayerDescriptor.xsd">
  <NamedLayer>
    <Name>roadway_label</Name>
    <UserStyle>
      <Title>巷道名称</Title>
      <FeatureTypeStyle>
        <Rule>
          <Name>label</Name>
          <MaxScaleDenominator>60000</MaxScaleDenominator>
          <TextSymbolizer>
            <Label><ogc:PropertyName>text</ogc:PropertyName></Label>
            <Font>
              <CssParameter name="font-family">WenQuanYi Micro Hei</CssParameter>
              <CssParameter name="font-size">12</CssParameter>
              <CssParameter name="font-weight">normal</CssParameter>
            </Font>
            <LabelPlacement>
              <PointPlacement>
                <AnchorPoint>
                  <AnchorPointX>0.5</AnchorPointX>
                  <AnchorPointY>0.5</AnchorPointY>
                </AnchorPoint>
                <Rotation>
                  <ogc:Function name="if_then_else">
                    <ogc:Function name="and">
                      <ogc:Function name="greaterThan">
                        <ogc:PropertyName>angle</ogc:PropertyName><ogc:Literal>90</ogc:Literal>
                      </ogc:Function>
                      <ogc:Function name="lessThan">
                        <ogc:PropertyName>angle</ogc:PropertyName><ogc:Literal>270</ogc:Literal>
                      </ogc:Function>
                    </ogc:Function>
                    <ogc:Sub><ogc:Literal>180</ogc:Literal><ogc:PropertyName>angle</ogc:PropertyName></ogc:Sub>
                    <ogc:Sub><ogc:Literal>0</ogc:Literal><ogc:PropertyName>angle</ogc:PropertyName></ogc:Sub>
                  </ogc:Function>
                </Rotation>
              </PointPlacement>
            </LabelPlacement>
            <!-- 白色光晕：中文字形方正、笔画密，光晕连成一片，看起来就是白底的小标签。
                 不用"图形拉伸当底板"（graphic-resize）：GeoServer 不会把底板跟着文字一起旋转，加了它文字就都变成横的 -->
            <Halo>
              <Radius>3.5</Radius>
              <Fill><CssParameter name="fill">#f4f7f6</CssParameter></Fill>
            </Halo>
            <Fill>
              <CssParameter name="fill">#111111</CssParameter>
            </Fill>
            <VendorOption name="conflictResolution">true</VendorOption>
            <VendorOption name="spaceAround">2</VendorOption>
            <VendorOption name="partials">false</VendorOption>
          </TextSymbolizer>
        </Rule>
      </FeatureTypeStyle>
    </UserStyle>
  </NamedLayer>
</StyledLayerDescriptor>
