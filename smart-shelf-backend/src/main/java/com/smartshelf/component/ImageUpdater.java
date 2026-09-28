package com.smartshelf.component;

import com.smartshelf.model.Product;
import com.smartshelf.repository.ProductRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Order(1)
public class ImageUpdater implements CommandLineRunner {

    private final ProductRepository productRepository;

    public ImageUpdater(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        Map<String, String> defaultImages = new HashMap<>();
        
        // Breads
        defaultImages.put("Classic French Baguette", "https://images.unsplash.com/photo-1549903072-7e6e0d654a1a?w=600&q=80");
        defaultImages.put("Brioche Loaf", "https://images.unsplash.com/photo-1608198093002-ad4e005484ec?w=600&q=80");
        defaultImages.put("Focaccia with Rosemary & Olive Oil", "https://images.unsplash.com/photo-1593005886470-f421f9859fbb?w=600&q=80");
        defaultImages.put("Ciabatta Loaf", "https://images.unsplash.com/photo-1614959145657-3f3366c05ff8?w=600&q=80");
        defaultImages.put("Country Rye Bread", "https://images.unsplash.com/photo-1509440159596-0249088772ff?w=600&q=80");
        defaultImages.put("Whole Wheat Multigrain Loaf", "https://images.unsplash.com/photo-1586444248902-2f64eddc13df?w=600&q=80");
        defaultImages.put("Challah Bread", "https://images.unsplash.com/photo-1598373182133-52452f7691ef?w=600&q=80");
        defaultImages.put("Pumpernickel Bread", "https://images.unsplash.com/photo-1589367920969-ab8e050eb0e9?w=600&q=80");
        defaultImages.put("Garlic Herb Loaf", "https://images.unsplash.com/photo-1573140247632-f8fd74997d5c?w=600&q=80");

        // Pastries
        defaultImages.put("Croissant", "https://images.unsplash.com/photo-1555507036-ab1f40ce88f4?w=600&q=80");
        defaultImages.put("Pain au Chocolat", "https://images.unsplash.com/photo-1626082895617-2c6ab38253a0?w=600&q=80");
        defaultImages.put("Almond Croissant", "https://images.unsplash.com/photo-1509365465985-25d11c17e812?w=600&q=80");
        defaultImages.put("Apple Turnover", "https://images.unsplash.com/photo-1600857544200-b2f666a9a2ec?w=600&q=80");
        defaultImages.put("Cinnamon Roll", "https://images.unsplash.com/photo-1509365465985-25d11c17e812?w=600&q=80"); // fallback
        defaultImages.put("Blueberry Danish", "https://images.unsplash.com/photo-1606353974488-066ebbc58b29?w=600&q=80");
        defaultImages.put("Raspberry Crown", "https://images.unsplash.com/photo-1563717208154-1eb31a89d0df?w=600&q=80");
        defaultImages.put("Pain aux Raisins", "https://images.unsplash.com/photo-1624372957169-df42718cd7c5?w=600&q=80");
        defaultImages.put("Pecan Maple Danish", "https://images.unsplash.com/photo-1628198754117-0624021295b9?w=600&q=80");

        // Cakes & Desserts
        defaultImages.put("Chocolate Fudge Cake Slice", "https://images.unsplash.com/photo-1578985545062-69928b1d9587?w=600&q=80");
        defaultImages.put("Red Velvet Cake Slice", "https://images.unsplash.com/photo-1614588820427-463d11b33036?w=600&q=80");
        defaultImages.put("Carrot Cake Slice", "https://images.unsplash.com/photo-1624009712061-f033a7f80db9?w=600&q=80");
        defaultImages.put("Lemon Drizzle Cake Slice", "https://images.unsplash.com/photo-1582293041079-7814c271e549?w=600&q=80");
        defaultImages.put("Tiramisu Cup", "https://images.unsplash.com/photo-1571115177098-24db0d4e4fd8?w=600&q=80");
        defaultImages.put("Chocolate Chip Muffin", "https://images.unsplash.com/photo-1607958996333-41aef7caefaa?w=600&q=80");
        defaultImages.put("Blueberry Streusel Muffin", "https://images.unsplash.com/photo-1558961363-fa8fdf82db35?w=600&q=80");
        defaultImages.put("Double Chocolate Muffin", "https://images.unsplash.com/photo-1621305417030-9b34032d9692?w=600&q=80");
        defaultImages.put("Classic Chocolate Chip Cookie", "https://images.unsplash.com/photo-1499636136210-6f4ee915583e?w=600&q=80");
        defaultImages.put("Oatmeal Raisin Cookie", "https://images.unsplash.com/photo-1590080874088-eec64895e423?w=600&q=80");
        defaultImages.put("Macadamia Nut Cookie", "https://images.unsplash.com/photo-1605342416298-65487771761e?w=600&q=80");
        defaultImages.put("Brownie Square", "https://images.unsplash.com/photo-1606313564200-e75d5e30476c?w=600&q=80");
        defaultImages.put("Walnut Brownie", "https://images.unsplash.com/photo-1587314168485-3236d6710814?w=600&q=80");
        defaultImages.put("Blondie Salted Caramel Bar", "https://images.unsplash.com/photo-1624467540209-4bf9d1209b53?w=600&q=80");
        defaultImages.put("Lemon Curd Bar", "https://images.unsplash.com/photo-1610444589332-957262ba99a1?w=600&q=80");
        defaultImages.put("Pecan Bar", "https://images.unsplash.com/photo-1514517220017-8ce97a34a7b6?w=600&q=80");

        // Savory
        defaultImages.put("Veg Puff", "https://images.unsplash.com/photo-1600326145359-3a44909d1a39?w=600&q=80");
        defaultImages.put("Egg Puff", "https://images.unsplash.com/photo-1550507992-eb63ffee0847?w=600&q=80");
        defaultImages.put("Chicken Puff", "https://images.unsplash.com/photo-1604908176997-125f25cc6f3d?w=600&q=80");
        defaultImages.put("Veg Roll", "https://images.unsplash.com/photo-1544025162-d76694265947?w=600&q=80");
        defaultImages.put("Chicken Roll", "https://images.unsplash.com/photo-1534422298391-e4f8c172dddb?w=600&q=80");
        defaultImages.put("Spinach & Feta Quiche", "https://images.unsplash.com/photo-1494850381669-e39062325902?w=600&q=80");
        defaultImages.put("Bacon & Mushroom Quiche", "https://images.unsplash.com/photo-1592323719011-370c9ddf09e2?w=600&q=80");

        // Beverages
        defaultImages.put("Hot Chocolate", "https://images.unsplash.com/photo-1542990253-0d0f5be5f0ed?w=600&q=80");
        defaultImages.put("Caramel Macchiato", "https://images.unsplash.com/photo-1485808191679-5f86510681a2?w=600&q=80");
        defaultImages.put("Cold Brew Coffee", "https://images.unsplash.com/photo-1461023058943-0708e5264024?w=600&q=80");

        String defaultBread = "https://images.unsplash.com/photo-1509440159596-0249088772ff?w=600&q=80";
        String defaultPastry = "https://images.unsplash.com/photo-1555507036-ab1f40ce88f4?w=600&q=80";
        String defaultCake = "https://images.unsplash.com/photo-1578985545062-69928b1d9587?w=600&q=80";
        String defaultSavory = "https://images.unsplash.com/photo-1600326145359-3a44909d1a39?w=600&q=80";
        String defaultBev = "https://images.unsplash.com/photo-1497935586351-b67a49e012bf?w=600&q=80";

        List<Product> products = productRepository.findAll();
        for (Product product : products) {
            if (product.getImageUrl() == null || product.getImageUrl().isEmpty()) {
                String img = defaultImages.get(product.getName());
                if (img == null) {
                    if (product.getCategory().equalsIgnoreCase("Breads")) img = defaultBread;
                    else if (product.getCategory().equalsIgnoreCase("Pastries")) img = defaultPastry;
                    else if (product.getCategory().equalsIgnoreCase("Cakes & Desserts")) img = defaultCake;
                    else if (product.getCategory().equalsIgnoreCase("Savory")) img = defaultSavory;
                    else if (product.getCategory().equalsIgnoreCase("Beverages")) img = defaultBev;
                    else img = defaultBread;
                }
                product.setImageUrl(img);
                productRepository.save(product);
                System.out.println("Updated image for " + product.getName());
            }
        }
    }
}
