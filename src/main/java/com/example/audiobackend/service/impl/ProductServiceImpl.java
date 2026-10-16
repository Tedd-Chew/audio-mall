package com.example.audiobackend.service.impl;

import com.example.audiobackend.constant.RedisConstant;
import com.example.audiobackend.entity.Product;
import com.example.audiobackend.mapper.ProductMapper;
import com.example.audiobackend.service.ProductService;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductServiceImpl.class);

    private final ProductMapper productMapper;

    private final StringRedisTemplate stringRedisTemplate;

    private final ThreadPoolExecutor threadPoolExecutor;

    private String getProductKey(Long id) {
        return RedisConstant.PRODUCT_KEY_PREFIX + id;
    }

    private String getViewCountKey(Integer productId) {
        return RedisConstant.PRODUCT_VIEW_COUNT_PREFIX + productId;
    }

    private String                                                                                                                                                                          getNullKey(Long id) {
        return RedisConstant.PRODUCT_NULL_PREFIX + id;
    }

    private String getLockKey(Long id) {
        return RedisConstant.LOCK_PRODUCT_PREFIX + id;
    }

    private void asyncAddViewCount(Integer productId) {
        try {
            String key = getViewCountKey(productId);
            threadPoolExecutor.execute(() -> {
                try {
                    stringRedisTemplate.opsForHash().increment(key, "viewCount", 1);
                    log.debug("异步增加浏览量成功，产品ID: {}", productId);
                } catch (Exception e) {
                    log.error("异步增加浏览量失败，产品ID: {}, 错误: {}", productId, e.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("提交异步任务失败，产品ID: {}, 错误: {}", productId, e.getMessage());
        }
    }

    @Override
    public List<Product> list() {
        try {
            QueryWrapper<Product> wrapper = new QueryWrapper<>();
            wrapper.eq("is_delete", 0);
            List<Product> products = productMapper.selectList(wrapper);
            log.info("查询产品列表成功，数量: {}", products.size());
            return products;
        } catch (Exception e) {
            log.error("查询产品列表失败，错误: {}", e.getMessage());
            throw new RuntimeException("查询产品列表失败", e);
        }
    }

    @Override
    public List<Product> listAll() {
        try {
            List<Product> products = productMapper.selectList(null);
            log.info("查询全部产品（含已隐藏）成功，数量: {}", products.size());
            return products;
        } catch (Exception e) {
            log.error("查询全部产品失败，错误: {}", e.getMessage());
            throw new RuntimeException("查询全部产品失败", e);
        }
    }

    @Override
    public Page<Product> getProductPage(Integer pageNum, Integer pageSize) {
        try {
            Page<Product> page = new Page<>(pageNum, pageSize);
            QueryWrapper<Product> wrapper = new QueryWrapper<>();
            wrapper.eq("is_delete", 0);
            Page<Product> result = productMapper.selectPage(page, wrapper);
            log.info("分页查询产品成功，当前页: {}, 每页数量: {}, 总数: {}",
                    pageNum, pageSize, result.getTotal());
            return result;
        } catch (Exception e) {
            log.error("分页查询产品失败，页码: {}, 每页数量: {}, 错误: {}",
                    pageNum, pageSize, e.getMessage());
            throw new RuntimeException("分页查询产品失败", e);
        }
    }

    @Override
    public Product getById(Long id) {
        if (id == null || id <= 0) {
            log.warn("查询产品详情失败，ID不合法: {}", id);
            return null;
        }

        long expireSeconds = 12 * 3600 + new Random().nextInt(30 * 60);
        String nullKey = getNullKey(id);

        try {
            if (stringRedisTemplate.hasKey(nullKey)) {
                log.debug("命中空值缓存，直接返回null，产品ID: {}", id);
                return null;
            }

            String redisKey = getProductKey(id);
            Map<Object, Object> hashData = stringRedisTemplate.opsForHash().entries(redisKey);

            if (!hashData.isEmpty()) {
                Product product = new Product();
                product.setId(id);
                product.setName((String) hashData.get("name"));
                product.setPrice(Double.parseDouble((String) hashData.get("price")));
                product.setDescription((String) hashData.get("description"));
                product.setImageUrl((String) hashData.get("imageUrl"));
                product.setCategoryId(hashData.get("categoryId") != null ? Integer.parseInt((String) hashData.get("categoryId")) : null);
                product.setImageList((String) hashData.get("imageList"));
                product.setTags((String) hashData.get("tags"));
                product.setDetail((String) hashData.get("detail"));
                product.setIsDelete(hashData.get("isDelete") != null ? Integer.parseInt((String) hashData.get("isDelete")) : 0);
                if (product.getIsDelete() == 1) {
                    log.debug("命中Redis缓存但产品已隐藏，产品ID: {}", id);
                    return null;
                }
                log.debug("命中Redis缓存，产品ID: {}", id);
                return product;
            }

            String lockKey = getLockKey(id);
            Boolean lock = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", 3, TimeUnit.SECONDS);

            if (lock == null || !lock) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                log.debug("未获取到分布式锁，重试获取缓存，产品ID: {}", id);
                return getById(id);
            }

            try {
                Product product = productMapper.selectById(id);
                    if (product != null) {
                        Map<String, String> map = new HashMap<>();
                        map.put("id", product.getId().toString());
                        map.put("name", product.getName());
                        map.put("price", product.getPrice().toString());
                        map.put("description", product.getDescription());
                        map.put("imageUrl", product.getImageUrl());
                        map.put("viewCount", product.getViewCount().toString());
                        map.put("categoryId", product.getCategoryId() != null ? product.getCategoryId().toString() : "");
                        map.put("imageList", product.getImageList());
                        map.put("tags", product.getTags());
                        map.put("detail", product.getDetail());
                        map.put("isDelete", product.getIsDelete() != null ? product.getIsDelete().toString() : "0");
                        stringRedisTemplate.opsForHash().putAll(redisKey, map);
                        stringRedisTemplate.expire(redisKey, expireSeconds, TimeUnit.SECONDS);
                        log.info("从数据库查询并写入缓存，产品ID: {}", id);

                    asyncAddViewCount(id.intValue());

                    if (product.getIsDelete() == 1) {
                        log.debug("产品已隐藏，不返回，产品ID: {}", id);
                        return null;
                    }
                } else {
                    stringRedisTemplate.opsForValue().setIfAbsent(nullKey, "true", 1, TimeUnit.MINUTES);
                    log.debug("产品不存在，缓存空值，产品ID: {}", id);
                }
                return product;
            } finally {
                stringRedisTemplate.delete(lockKey);
                log.debug("释放分布式锁，产品ID: {}", id);
            }
        } catch (Exception e) {
            log.error("查询产品详情异常，产品ID: {}, 错误: {}", id, e.getMessage());
            throw new RuntimeException("查询产品详情失败", e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean add(Product product) {
        try {
            if (product == null) {
                log.warn("添加产品失败，产品对象为null");
                return false;
            }
            product.setId(null);
            
            if (product.getPrice() == null) {
                product.setPrice(0.0);
            }
            if (product.getViewCount() == null) {
                product.setViewCount(0);
            }
            if (product.getIsDelete() == null) {
                product.setIsDelete(0);//设置默认值
            }
            boolean success = productMapper.insert(product) > 0;
            if (success) {
                String nullKey = getNullKey(product.getId());
                stringRedisTemplate.delete(nullKey);
                log.info("添加产品成功，产品ID: {}, 产品名称: {}", product.getId(), product.getName());
            } else {
                log.warn("添加产品失败，产品信息: {}", product);
            }
            return success;
        } catch (Exception e) {
            if (product != null) {
                log.error("添加产品异常，产品名称: {}, 错误: {}", product.getName(), e.getMessage());
            }
            throw new RuntimeException("添加产品失败", e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(Product product) {
        try {
            if (product == null || product.getId() == null) {
                log.warn("更新产品失败，产品对象或ID为null");
                return false;
            }
            boolean success = productMapper.updateById(product) > 0;
            if (success) {
                String redisKey = getProductKey(product.getId());
                stringRedisTemplate.delete(redisKey);
                log.info("更新产品成功并删除缓存，产品ID: {}", product.getId());
            } else {
                log.warn("更新产品失败，产品ID: {}", product.getId());
            }
            return success;
        } catch (Exception e) {
            if (product != null) {
                log.error("更新产品异常，产品ID: {}, 错误: {}", product.getId(), e.getMessage());
            }
            throw new RuntimeException("更新产品失败", e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleDelete(Long id) {
        try {
            if (id == null || id <= 0) {
                log.warn("切换产品状态失败，ID不合法: {}", id);
                return false;
            }
            Product product = productMapper.selectById(id);
            if (product == null) {
                log.warn("切换产品状态失败，产品不存在，ID: {}", id);
                return false;
            }
            product.setIsDelete(product.getIsDelete() == 1 ? 0 : 1);
            boolean success = productMapper.updateById(product) > 0;
            if (success) {
                String redisKey = getProductKey(id);
                stringRedisTemplate.delete(redisKey);
                log.info("切换产品状态成功，产品ID: {}, 新状态: {}", id, product.getIsDelete());
            }
            return success;
        } catch (Exception e) {
            log.error("切换产品状态异常，产品ID: {}, 错误: {}", id, e.getMessage());
            throw new RuntimeException("切换产品状态失败", e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(Long id) {
        try {
            if (id == null || id <= 0) {
                log.warn("删除产品失败，ID不合法: {}", id);
                return false;
            }
            boolean success = productMapper.deleteById(id) > 0;
            if (success) {
                String redisKey = getProductKey(id);
                stringRedisTemplate.delete(redisKey);
                log.info("删除产品成功并删除缓存，产品ID: {}", id);
            } else {
                log.warn("删除产品失败，产品ID: {}", id);
            }
            return success;
        } catch (Exception e) {
            log.error("删除产品异常，产品ID: {}, 错误: {}", id, e.getMessage());
            throw new RuntimeException("删除产品失败", e);
        }
    }
}