package cn.beehive.cell.openai.service.impl;

import cn.beehive.base.domain.entity.RoomOpenAiImageMsgDO;
import cn.beehive.base.domain.query.RoomMsgCursorQuery;
import cn.beehive.base.enums.CellCodeEnum;
import cn.beehive.base.enums.MessageStatusEnum;
import cn.beehive.base.enums.MessageTypeEnum;
import cn.beehive.base.handler.mp.BeehiveServiceImpl;
import cn.beehive.base.mapper.RoomOpenAiImageMsgMapper;
import cn.beehive.base.util.FrontUserUtil;
import cn.beehive.base.util.ObjectMapperUtil;
import cn.beehive.base.util.OkHttpClientUtil;
import cn.beehive.cell.core.hander.strategy.CellConfigFactory;
import cn.beehive.cell.core.hander.strategy.CellConfigStrategy;
import cn.beehive.cell.core.hander.strategy.DataWrapper;
import cn.beehive.cell.openai.domain.request.RoomOpenAiImageSendRequest;
import cn.beehive.cell.openai.domain.vo.RoomOpenAiImageMsgVO;
import cn.beehive.cell.openai.enums.OpenAiImageCellConfigCodeEnum;
import cn.beehive.cell.openai.handler.converter.RoomOpenAiImageMsgConverter;
import cn.beehive.cell.openai.service.RoomOpenAiImageMsgService;
import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.unfbx.chatgpt.OpenAiClient;
import com.unfbx.chatgpt.entity.images.Image;
import com.unfbx.chatgpt.entity.images.ImageResponse;
import com.unfbx.chatgpt.entity.images.Item;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author hncboy
 * @date 2023/6/3
 * OpenAi 图像房间消息服务层实现类
 */
@Slf4j
@Service
public class RoomOpenAiImageMsgServiceImpl extends BeehiveServiceImpl<RoomOpenAiImageMsgMapper, RoomOpenAiImageMsgDO> implements RoomOpenAiImageMsgService {

    @Resource
    private CellConfigFactory cellConfigFactory;

    @Override
    public List<RoomOpenAiImageMsgVO> list(RoomMsgCursorQuery cursorQuery) {
        List<RoomOpenAiImageMsgDO> cursorList = cursorList(cursorQuery, RoomOpenAiImageMsgDO::getId, new LambdaQueryWrapper<RoomOpenAiImageMsgDO>()
                .eq(RoomOpenAiImageMsgDO::getUserId, FrontUserUtil.getUserId())
                .eq(RoomOpenAiImageMsgDO::getRoomId, cursorQuery.getRoomId()));
        return RoomOpenAiImageMsgConverter.INSTANCE.entityToVO(cursorList);
    }

    @Override
    public RoomOpenAiImageMsgVO send(RoomOpenAiImageSendRequest sendRequest) {
        // 获取房间配置参数
        CellConfigStrategy cellConfigStrategy = cellConfigFactory.getCellConfigStrategy(sendRequest.getRoomId(), CellCodeEnum.OPENAI_IMAGE);
        Map<OpenAiImageCellConfigCodeEnum, DataWrapper> roomConfigParamAsMap = cellConfigStrategy.getRoomConfigParamAsMap(sendRequest.getRoomId());

        String apiKey = roomConfigParamAsMap.get(OpenAiImageCellConfigCodeEnum.API_KEY).asString();

        // 构建图像生成参数
        Image image = Image.builder()
                .prompt(sendRequest.getPrompt())
                .size(roomConfigParamAsMap.get(OpenAiImageCellConfigCodeEnum.SIZE).asString())
                .build();

        // 创建问题消息
        RoomOpenAiImageMsgDO questionMessage = new RoomOpenAiImageMsgDO();
        questionMessage.setUserId(FrontUserUtil.getUserId());
        questionMessage.setRoomId(sendRequest.getRoomId());
        questionMessage.setMessageType(MessageTypeEnum.QUESTION);
        questionMessage.setApiKey(apiKey);
        questionMessage.setSize(image.getSize());
        questionMessage.setPrompt(sendRequest.getPrompt());
        questionMessage.setOriginalData(ObjectMapperUtil.toJson(image));
        questionMessage.setStatus(MessageStatusEnum.INIT);
        questionMessage.setRoomConfigParamJson(ObjectMapperUtil.toJson(roomConfigParamAsMap));
        save(questionMessage);

        // 构建 OpenAiClient
        OpenAiClient openAiClient = OpenAiClient.builder()
                .apiKey(Collections.singletonList(apiKey))
                .okHttpClient(OkHttpClientUtil.getInstance())
                .apiHost(roomConfigParamAsMap.get(OpenAiImageCellConfigCodeEnum.OPENAI_BASE_URL).asString())
                .build();

        // 构建回答消息
        RoomOpenAiImageMsgDO answerMessage = new RoomOpenAiImageMsgDO();
        answerMessage.setUserId(questionMessage.getUserId());
        answerMessage.setRoomId(questionMessage.getRoomId());
        answerMessage.setParentQuestionMessageId(questionMessage.getId());
        answerMessage.setMessageType(MessageTypeEnum.ANSWER);
        answerMessage.setApiKey(questionMessage.getApiKey());
        answerMessage.setSize(questionMessage.getSize());
        answerMessage.setPrompt(questionMessage.getPrompt());
        answerMessage.setRoomConfigParamJson(questionMessage.getRoomConfigParamJson());

        try {
            ImageResponse imageResponse = openAiClient.genImages(image);
            answerMessage.setOriginalData(ObjectMapperUtil.toJson(imageResponse));

            if (Objects.isNull(imageResponse) || CollUtil.isEmpty(imageResponse.getData())) {
                answerMessage.setResponseErrorData("生成图片数据为空");
                answerMessage.setStatus(MessageStatusEnum.FAILURE);
            } else {
                Item item = imageResponse.getData().get(0);
                answerMessage.setOpenaiImageUrl(item.getUrl());
                answerMessage.setImageName("TODO");
                answerMessage.setStatus(MessageStatusEnum.SUCCESS);
            }
        } catch (Exception e) {
            log.error("OpenAi 图像生成异常", e);
            answerMessage.setResponseErrorData("图像生成异常：" + e.getMessage());
            answerMessage.setStatus(MessageStatusEnum.FAILURE);
        }

        // 保存回答消息
        save(answerMessage);
        questionMessage.setStatus(answerMessage.getStatus());
        // 更新问题消息
        updateById(questionMessage);

        return RoomOpenAiImageMsgConverter.INSTANCE.entityToVO(answerMessage);
    }
}