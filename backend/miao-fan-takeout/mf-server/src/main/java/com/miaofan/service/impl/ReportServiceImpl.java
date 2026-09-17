package com.miaofan.service.impl;

import ch.qos.logback.classic.Logger;
import com.miaofan.dto.GoodsSalesDTO;
import com.miaofan.entity.Orders;
import com.miaofan.mapper.OrderMapper;
import com.miaofan.mapper.UserMapper;
import com.miaofan.service.ReportService;
import com.miaofan.service.WorkspaceService;
import com.miaofan.vo.*;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.asm.Advice;
import org.apache.commons.lang.StringUtils;
import org.apache.ibatis.annotations.Param;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import static com.fasterxml.jackson.databind.type.LogicalType.Map;

@Slf4j
@Service
public class ReportServiceImpl implements ReportService {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private WorkspaceService workspaceService;

    private ArrayList<LocalDate> getDatesList(LocalDate begin, LocalDate end) {
        ArrayList<LocalDate> dates = new ArrayList<>();
        dates.add(begin);
        while (begin.isBefore(end)) {
            begin = begin.plusDays(1);
            dates.add(begin);
        }

        return dates;
    }

    /**
     * 统计营业额
     */
    public TurnoverReportVO getTurnoverStatistics(LocalDate begin, LocalDate end) {
        /*//日期字符串
        ArrayList<LocalDate> dates = new ArrayList<>();
        dates.add(begin);
        while (begin.isBefore(end)) {
            begin=begin.plusDays(1);
            dates.add(begin);
        }*/

        ArrayList<LocalDate> dates = getDatesList(begin, end);

        //统计营业额
        ArrayList<Double> turnover = new ArrayList<>();
        for (LocalDate date : dates) {
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            //LocalTime.MIN只表示00:00这个时刻,没有只是哪一天,of用于拼接日期和时刻
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);

            Double sumTurnover = orderMapper.sumTurnover(beginTime, endTime, Orders.COMPLETED);
            sumTurnover = sumTurnover == null ? 0.0 : sumTurnover;
            turnover.add(sumTurnover);
        }


        return TurnoverReportVO.builder()
                .dateList(StringUtils.join(dates, ","))
                .turnoverList(StringUtils.join(turnover, ","))
                .build();
    }

    /**
     * 用户数据统计
     *
     * @param begin
     * @param end
     * @return
     */
    public UserReportVO getUserStatistics(LocalDate begin, LocalDate end) {
        //获得日期字符串
        ArrayList<LocalDate> dates = getDatesList(begin, end);

        //截止到每日的用户总数
        ArrayList<Integer> totalUserList = new ArrayList<>();
        //当日新增的用户总数
        ArrayList<Integer> newUserList = new ArrayList<>();

        for (LocalDate date : dates) {
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            //LocalTime.MIN只表示00:00这个时刻,没有只是哪一天,of用于拼接日期和时刻
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);

            newUserList.add(userMapper.countUsers(beginTime, endTime));
            totalUserList.add(userMapper.countUsers(null, endTime));
        }

        return UserReportVO.builder()
                .dateList(StringUtils.join(dates, ","))
                .newUserList(StringUtils.join(newUserList, ","))
                .totalUserList(StringUtils.join(totalUserList, ","))
                .build();
    }

    /**
     * 查询当天订单总数,有效订单总数
     *
     * @param begin
     * @param end
     * @return
     */
    @Override
    public OrderReportVO getOrdersStatistics(LocalDate begin, LocalDate end) {
        //获得日期字符串
        ArrayList<LocalDate> dates = getDatesList(begin, end);


        ArrayList<Integer> orderCountList = new ArrayList<>();
        ArrayList<Integer> validOrderCountList = new ArrayList<>();

        for (LocalDate date : dates) {
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);

            //当天有效订单总数
            Integer validOrderCount = orderMapper.countOrders(beginTime, endTime, Orders.COMPLETED);
            validOrderCountList.add(validOrderCount);

            //当天订单总数
            Integer orderCount = orderMapper.countOrders(beginTime, endTime, null);
            orderCountList.add(orderCount);
        }

        Integer totalValid = validOrderCountList.stream().reduce(Integer::sum).get();
        Integer totalOrders = orderCountList.stream().reduce(Integer::sum).get();

        Double orderCompletionRate = 0.0;
        if (totalValid != 0) {
            orderCompletionRate = totalValid.doubleValue() / totalOrders;
        }

        return OrderReportVO.builder()
                .dateList(StringUtils.join(dates, ","))
                .orderCountList(StringUtils.join(orderCountList, ","))
                .validOrderCountList(StringUtils.join(validOrderCountList, ","))
                .orderCompletionRate(orderCompletionRate)
                .validOrderCount(totalValid)
                .totalOrderCount(totalOrders)
                .build();
    }

    /**
     * 销量排名top10
     *
     * @param begin
     * @param end
     * @return
     */
    @Override
    public SalesTop10ReportVO getSalesTop10(LocalDate begin, LocalDate end) {
        log.info("销量排名");
        ArrayList<GoodsSalesDTO> goodsSalesDTOS = new ArrayList<>();

        LocalDateTime beginTime = LocalDateTime.of(begin, LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(end, LocalTime.MAX);

        goodsSalesDTOS = orderMapper.getSalesTop10(beginTime, endTime);

        List<String> nameList = goodsSalesDTOS.stream().map(GoodsSalesDTO::getName).toList();
        List<Integer> numberList = goodsSalesDTOS.stream().map(GoodsSalesDTO::getNumber).toList();

        return SalesTop10ReportVO.builder()
                .nameList(StringUtils.join(nameList, ","))
                .numberList(StringUtils.join(numberList, ","))
                .build();
    }

    @Override
    public void exportBusinessData(HttpServletResponse response) {
        //读取近三十天的数据
        LocalDate begin = LocalDate.now().minusDays(30);
        LocalDate end = LocalDate.now().minusDays(1);//今天可能没有结束

        BusinessDataVO businessData = workspaceService.getBusinessData(LocalDateTime.of(begin, LocalTime.MIN), LocalDateTime.of(end, LocalTime.MAX));


        try {
            // 用 ClassPathResource 读模板: 打成 jar 之后, new File(相对路径) 这种写法是找不到资源的
            InputStream in = new ClassPathResource("template/运营数据报表模板.xlsx").getInputStream();

            //创建一个基于当前模板的文件
            XSSFWorkbook excel = new XSSFWorkbook(in);

            XSSFSheet sheet = excel.getSheet("Sheet1");

            // 报表是对外文件, 左上角放"秒"字品牌标识
            insertBrandLogo(excel, sheet);

//第二行
            sheet.getRow(1).getCell(1).setCellValue("时间:" + begin + "至:" + end);
//第四行
            sheet.getRow(3).getCell(2).setCellValue(businessData.getTurnover());
            sheet.getRow(3).getCell(4).setCellValue(businessData.getOrderCompletionRate());
            sheet.getRow(3).getCell(6).setCellValue(businessData.getNewUsers());

            //第五行
            sheet.getRow(4).getCell(2).setCellValue(businessData.getValidOrderCount());
            sheet.getRow(4).getCell(4).setCellValue(businessData.getUnitPrice());


            for (int i = 0; i < 30; i++) {
                LocalDate date = begin.plusDays(i);

                XSSFRow row = sheet.getRow(7 + i);

                BusinessDataVO dataVO = workspaceService.getBusinessData(LocalDateTime.of(date, LocalTime.MIN), LocalDateTime.of(date, LocalTime.MAX));

                row.getCell(1).setCellValue(String.valueOf(date));
                row.getCell(2).setCellValue(dataVO.getTurnover());
                row.getCell(3).setCellValue(dataVO.getValidOrderCount());
                row.getCell(4).setCellValue(dataVO.getOrderCompletionRate());
                row.getCell(5).setCellValue(dataVO.getUnitPrice());
                row.getCell(6).setCellValue(dataVO.getNewUsers());
            }

            ServletOutputStream out = response.getOutputStream();
            excel.write(out);

            out.close();
            excel.close();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        //用输出流将文件输出
    }

    /**
     * 在报表左上角插入品牌标识
     * <p>
     * 模板里标题与数据从 B 列开始, A 列是空的, 所以锚点放在 A 列不会遮挡内容。
     * 插图只是锦上添花, 失败不影响导出, 因此异常只记录日志。
     */
    private void insertBrandLogo(XSSFWorkbook excel, XSSFSheet sheet) {
        try (InputStream logoStream = new ClassPathResource("static/img/brand/logo-text.png").getInputStream()) {
            byte[] logoBytes = StreamUtils.copyToByteArray(logoStream);
            int pictureIndex = excel.addPicture(logoBytes, Workbook.PICTURE_TYPE_PNG);

            XSSFDrawing drawing = sheet.createDrawingPatriarch();
            XSSFClientAnchor anchor = new XSSFClientAnchor(0, 0, 0, 0, 0, 0, 0, 4);
            drawing.createPicture(anchor, pictureIndex);
        } catch (Exception e) {
            log.warn("报表插入品牌标识失败, 跳过继续导出: {}", e.getMessage());
        }
    }
}
