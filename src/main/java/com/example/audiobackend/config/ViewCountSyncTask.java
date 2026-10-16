package com.example.audiobackend.config;
import java.util.Set;

//这是一个定时任务，用于定期把redis中的浏览量同步到数据库中，防止数据丢失。
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import com.example.audiobackend.entity.Product;
import com.example.audiobackend.mapper.ProductMapper;

@Component
public class ViewCountSyncTask {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ProductMapper productMapper;//注入mapper操作数据库

    //定时任务
    @Scheduled(cron = "0 0/5 * * * ?")//每5分钟执行一次
    @Transactional(rollbackFor = Exception.class)//开启事务，保证数据一致性，如果同步过程中发生异常，事务会回滚，避免数据库中出现不完整的数据。
    public void syncViewCountToMysql() {
        Set<String> keys = stringRedisTemplate.keys("product:*");//把redis中所有以product:viewCount:开头的key都取出来
        if(keys != null&&!keys.isEmpty()) {//防止keys对象不存在或者没有元素，导致空指针异常
            return;
        }
        // 2. 遍历每个商品
        for (String key : keys) {
            try {
                // ==============================================
                // 只拿 id 和 viewCount 两个字段！！！
                // ==============================================
                String idStr = (String) stringRedisTemplate.opsForHash().get(key, "id");
                String viewCountStr = (String) stringRedisTemplate.opsForHash().get(key, "viewCount");

                if (idStr == null || viewCountStr == null) {
                    continue;
                }

                Long id = Long.parseLong(idStr);
                Integer viewCount = Integer.parseInt(viewCountStr);

                // 3. 直接更新数据库！！！
                Product product = new Product();
                product.setId(id);
                product.setViewCount(viewCount);
                productMapper.updateById(product);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}

