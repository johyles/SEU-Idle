package com.hippo.fresh.search;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hippo.fresh.exception.ProductNotExistException;
import com.hippo.fresh.utils.ResponseUtils;
import org.elasticsearch.common.lucene.search.function.FunctionScoreQuery;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class SearchProductService {
    static final String PRODUCT_INDEX = "productindex";
    ////    public List<IndexedObjectInformation> createProductIndexBulk(final List<SearchProduct> searchProducts);
////    public String createProductIndex(SearchProduct searchProduct);
//    public void createProductIndexBulk(final List<SearchProduct> products);
////    public void createProductIndex(final SearchProduct product);
////    public void findProductsByName(final String name);
////    public void findProductsByPrice(final Double upperBound, final Double lowerBound);
//    public List<SearchProduct> processSearch(final String query);
//    public List<String> fetchSuggestions(String query);
    private ElasticsearchOperations elasticsearchOperations;

//    private SearchProductRepository searchProductRepository;
//
//    @Autowired
//    public SearchProductService(final ElasticsearchOperations elasticsearchOperations, final SearchProductRepository searchProductRepository) {
//        super();
//        this.elasticsearchOperations = elasticsearchOperations;
//        this.searchProductRepository = searchProductRepository;
//    }
//
//    public void createProductIndexBulk(final List<SearchProduct> products) {
//        searchProductRepository.saveAll(products);
//    }

    //fuzz????????????
    public ResponseUtils processSearch(int page, int pageNum, String productName, int sort, int order, int upperBound, int lowerBound) {
        // 1. Create query on multiple fields enabling fuzzy search
        Query searchQuery;

        //??????????????????????????????????????????????????????
        if (sort == 0) {
//            //?????????????????????????????? ??????id?????????????????????
            List<FunctionScoreQueryBuilder.FilterFunctionBuilder> filterFunctionBuilders = new ArrayList<>();
            filterFunctionBuilders.add(
                    new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                            QueryBuilders.termQuery("name", productName), ScoreFunctionBuilders.weightFactorFunction(80)));
//                            QueryBuilders.matchPhraseQuery("name", productName), ScoreFunctionBuilders.weightFactorFunction(50)));/
            filterFunctionBuilders.add(
                    new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                            QueryBuilders.matchPhraseQuery("detail", productName), ScoreFunctionBuilders.weightFactorFunction(5)));
            filterFunctionBuilders.add(
                    new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                            QueryBuilders.matchPhraseQuery("categoryFirst", productName), ScoreFunctionBuilders.weightFactorFunction(10)));
            filterFunctionBuilders.add(
                    new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                            QueryBuilders.matchPhraseQuery("categorySecond", productName), ScoreFunctionBuilders.weightFactorFunction(15)));

            //Combine
            FunctionScoreQueryBuilder.FilterFunctionBuilder[] builders = new FunctionScoreQueryBuilder.FilterFunctionBuilder[filterFunctionBuilders.size()];
            filterFunctionBuilders.toArray(builders);
            FunctionScoreQueryBuilder functionScoreQueryBuilder = QueryBuilders.functionScoreQuery(builders)
                    .scoreMode(FunctionScoreQuery.ScoreMode.SUM)
                    .setMinScore(2);

            BoolQueryBuilder boolQueryBuilder =
                    QueryBuilders.boolQuery()
                            //??????????????????
                            .must(QueryBuilders.termQuery("status", 1))
                            //?????????????????????????????? ??????id?????????????????????
//                            .must(QueryBuilders.matchPhraseQuery("name",productName).boost(4))
//                            .must(QueryBuilders.matchPhraseQuery("detail",productName).boost(2))
                            //??????????????????
                            .must(QueryBuilders.rangeQuery("price").from(lowerBound).to(upperBound));

            searchQuery = new NativeSearchQueryBuilder()
                    .withQuery(functionScoreQueryBuilder)
                    .withFilter(boolQueryBuilder)
                    //????????????
                    .withPageable(PageRequest.of(page, pageNum))
                    .build();
        } else {
            //??????????????????????????????
            BoolQueryBuilder boolQueryBuilder =
                    QueryBuilders.boolQuery()
//                        .must(QueryBuilders.matchPhrasePrefixQuery(productName, "name"))
                            .must(QueryBuilders.termQuery("status", 1))
                            .must(QueryBuilders.matchPhrasePrefixQuery("name", productName))
//                            .must(QueryBuilders.multiMatchQuery(productName, "name", "detail","categoryFirst","categorySecond")
//                                    .fuzziness(Fuzziness.AUTO))
                            //??????????????????
                            .must(QueryBuilders.rangeQuery("price").from(lowerBound).to(upperBound));
            //??????
            switch (sort) {
                case 1:
                    searchQuery = new NativeSearchQueryBuilder()
                            .withFilter(boolQueryBuilder)
                            //??????????????????
                            //??????????????????
                            .withSort(SortBuilders.fieldSort("salesAmount").order(order == 1 ? SortOrder.DESC : SortOrder.ASC))
                            //????????????
                            .withPageable(PageRequest.of(page, pageNum))
                            .build();
                    break;
                case 2:
                    searchQuery = new NativeSearchQueryBuilder()
                            .withFilter(boolQueryBuilder)
                            //??????????????????
                            //??????????????????
                            .withSort(SortBuilders.fieldSort("price").order(order == 1 ? SortOrder.DESC : SortOrder.ASC))
                            //????????????
                            .withPageable(PageRequest.of(page, pageNum))
                            .build();
                    break;
                case 3:
                    searchQuery = new NativeSearchQueryBuilder()
                            .withFilter(boolQueryBuilder)
                            //??????????????????
                            //??????????????????
                            .withSort(SortBuilders.fieldSort("score").order(order == 1 ? SortOrder.DESC : SortOrder.ASC))
                            //????????????
                            .withPageable(PageRequest.of(page, pageNum))
                            .build();
                    break;
                default:
                    searchQuery = new NativeSearchQueryBuilder()
                            .withFilter(boolQueryBuilder)
                            //????????????
                            .withPageable(PageRequest.of(page, pageNum))
                            .build();
            }
        }
        // 2. Execute search
        SearchHits<SearchProduct> productHits =
                elasticsearchOperations
                        .search(searchQuery, SearchProduct.class,
                                IndexCoordinates.of(PRODUCT_INDEX));

        // 3. Map searchHits to product list
        List<SearchProduct> productMatches = new ArrayList<SearchProduct>();
        productHits.forEach(searchHit -> {
            productMatches.add(searchHit.getContent());
        });

        //?????????????????????????????? ????????????
        if (productMatches.size() == 0)
            throw new ProductNotExistException(null);

        JSONArray jsonArray = new JSONArray();
        JSONObject jsonObject = new JSONObject();

        double maxPrice = GetPrice(productName, 1, upperBound, lowerBound).get(0).getPrice();
        double minPrice = GetPrice(productName, 2, upperBound, lowerBound).get(0).getPrice();
        jsonObject.put("searchHit", productHits.getTotalHits());
        jsonObject.put("maxPrice", maxPrice);
        jsonObject.put("minPrice", minPrice);

        jsonArray.add(productMatches);
        jsonArray.add(jsonObject);
        return ResponseUtils.success("????????????", jsonArray);
    }



    //????????????????????????
    public List<SearchProduct> GetPrice(String productName, int order, int upperBound, int lowerBound) {
        // 1. Create query on multiple fields enabling fuzzy search
        Query searchQuery;
        //??????????????????????????????
        BoolQueryBuilder boolQueryBuilder =
                QueryBuilders.boolQuery()
                        .must(QueryBuilders.termQuery("status", 1))
                        .must(QueryBuilders.matchPhrasePrefixQuery("name", productName))
                        //??????????????????
                        .must(QueryBuilders.rangeQuery("price").from(lowerBound).to(upperBound));
        //??????
        searchQuery = new NativeSearchQueryBuilder()
                .withFilter(boolQueryBuilder)
                //??????????????????
                //??????????????????
                .withSort(SortBuilders.fieldSort("price").order(order == 1 ? SortOrder.DESC : SortOrder.ASC))
                //????????????
                .withPageable(PageRequest.of(0, 1))
                .build();
        // 2. Execute search
        SearchHits<SearchProduct> productHits =
                elasticsearchOperations
                        .search(searchQuery, SearchProduct.class,
                                IndexCoordinates.of(PRODUCT_INDEX));
        // 3. Map searchHits to product list
        List<SearchProduct> productMatches = new ArrayList<SearchProduct>();
        productHits.forEach(searchHit -> {
            productMatches.add(searchHit.getContent());
        });

        //?????????????????????????????? ????????????
        if (productMatches.size() == 0)
            throw new ProductNotExistException(null);
        return productMatches;
    }
}
